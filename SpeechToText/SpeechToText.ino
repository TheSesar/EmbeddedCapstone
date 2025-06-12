// ------------------------------------------------------------------------------------------------------------------------------
// ----------------                                    University of Washington                                  ----------------
// ----------------                            Smart Glasses: Real-time Language Translator                      ----------------   
// ----------------                        ECE/CSE 475: Embedded Systems Captone Project Team 9                  ----------------
// ----------------                                      Speech-to-Text Module                                   ----------------  
// ----------------                                         June 10th, 2025                                       ----------------
// ------------------------------------------------------------------------------------------------------------------------------

#include <ESP_I2S.h>  // I2S Audio connection header file for esp32 version 3.0.x
#include <WiFi.h>     // Wifi header to connect to wifi using esp32s3 antenna
#include <SD.h>       // SD card reader header to save .wav files into SD card and retrieve them to get the trancripts
#include "FS.h"
#include <Arduino.h>
#include <U8x8lib.h>  // Display

// BLE
#include <ArduinoBLE.h>
#include <SPI.h>

// Camera
#include <esp_camera.h>

// Variables to be used in the recording program, do not change for best
#define SAMPLE_RATE 16000U
#define SAMPLE_BITS 16
#define WAV_HEADER_SIZE 44
#define VOLUME_GAIN 2
#define AUDIO_FILE "/Audio.wav" // audio file name to be saved into SD and translated to text
#define BUTTON_PIN 2            // D1 on ESP32 board
#define CHARACTERISTIC_SIZE 512

// Camera Pins
#define PWDN_GPIO_NUM     -1
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM     10
#define SIOD_GPIO_NUM     40
#define SIOC_GPIO_NUM     39
#define Y9_GPIO_NUM       48
#define Y8_GPIO_NUM       11
#define Y7_GPIO_NUM       12
#define Y6_GPIO_NUM       14
#define Y5_GPIO_NUM       16
#define Y4_GPIO_NUM       18
#define Y3_GPIO_NUM       17
#define Y2_GPIO_NUM       15
#define VSYNC_GPIO_NUM    38
#define HREF_GPIO_NUM     47
#define PCLK_GPIO_NUM     13

// Initializing display
U8X8_SSD1306_128X64_NONAME_HW_I2C u8x8(SCL, SDA, U8X8_PIN_NONE);

// BLE
const char* serviceUUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"; // Custom service UUID
const char* rxCharUUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";  // Metadata characteristic UUID (read/notify)
const char* txCharUUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";  // Image/data characteristic UUID (write/notify)

BLEService myService(serviceUUID);
BLECharacteristic rxCharacteristic(rxCharUUID, BLERead | BLENotify, CHARACTERISTIC_SIZE);
BLECharacteristic txCharacteristic(txCharUUID, BLERead | BLEWrite | BLENotify, CHARACTERISTIC_SIZE);
BLEDescriptor myDescriptor("00002902-0000-1000-8000-00805f9b34fb", "0");

BLEDevice central;
bool bleConnected = false;
I2SClass I2S;         // define I2S
File file;            // define File
bool isWIFIConnected; // flag to check if Wifi is connected

volatile bool isRecording = false;
TaskHandle_t recordTaskHandle = NULL;

void setup() {
  Serial.begin(115200); // setup the baud rate
  while (!Serial);      // wait till connect to the correct rate

  // setup display
  u8x8.begin();
  u8x8.setFlipMode(1);
  u8x8.setFont(u8x8_font_chroma48medium8_r);

  // setup 42 PDM clock and 41 PDM data pins
  I2S.setPinsPdmRx(42, 41);

  //The transmission mode is PDM_MONO_MODE, which means that PDM (pulse density modulation) mono mode is used for transmission
  if (!I2S.begin(I2S_MODE_PDM_RX, 16000, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO)) {
    Serial.println("Failed to initialize I2S!");
    while (1);
  }

  // D2 is pin for expansion board; use pin 21 for esp32s3 sense (w/camera and SD slot)
  if (!SD.begin(D2)) {
    Serial.println("Failed to mount SD Card!");
    while (1);
  }

  // Setup button
  pinMode(BUTTON_PIN, INPUT_PULLUP);  // use INPUT_PULLUP if using embedded button, INPUT else (with externel resistor connection)

  pinMode(LED_BUILTIN, OUTPUT);     // LED to show recording
  digitalWrite(LED_BUILTIN, HIGH);  // LED is off initially

  // Connect Wi-Fi
  xTaskCreate(wifiConnect, "wifi_Connect", 4096, NULL, 0, NULL);  // task scheduling with freeRTOS

  // setting up BLE
  if (!BLE.begin()) {
    Serial.println("Starting BLE failed!");
    while (1);
  }

  BLE.setLocalName("SmartGlassesMCU");
  BLE.setAdvertisedService(myService);

  myService.addCharacteristic(txCharacteristic);
  myService.addCharacteristic(rxCharacteristic);
  txCharacteristic.addDescriptor(myDescriptor);

  BLE.addService(myService);

  rxCharacteristic.setValue("Ready to receive");
  txCharacteristic.setValue("Ready to send");

  BLE.advertise();
  Serial.println("BLE advertising started...");

  // Camera setup
  camera_config_t config = {
    .pin_pwdn = PWDN_GPIO_NUM, 
    .pin_reset = RESET_GPIO_NUM,
    .pin_xclk = XCLK_GPIO_NUM, 
    .pin_sscb_sda = SIOD_GPIO_NUM, 
    .pin_sscb_scl = SIOC_GPIO_NUM,
    .pin_d7 = Y9_GPIO_NUM, 
    .pin_d6 = Y8_GPIO_NUM, 
    .pin_d5 = Y7_GPIO_NUM, 
    .pin_d4 = Y6_GPIO_NUM,
    .pin_d3 = Y5_GPIO_NUM, 
    .pin_d2 = Y4_GPIO_NUM, 
    .pin_d1 = Y3_GPIO_NUM, 
    .pin_d0 = Y2_GPIO_NUM,
    .pin_vsync = VSYNC_GPIO_NUM, 
    .pin_href = HREF_GPIO_NUM, 
    .pin_pclk = PCLK_GPIO_NUM,
    .xclk_freq_hz = 20000000, 
    .ledc_timer = LEDC_TIMER_0, 
    .ledc_channel = LEDC_CHANNEL_0,
    .pixel_format = PIXFORMAT_JPEG, 
    .frame_size = FRAMESIZE_VGA, 
    .jpeg_quality = 12,
    .fb_count = 1
  };
  if (esp_camera_init(&config) != ESP_OK) {
    Serial.println("Camera failed");
    while (1);
  }
}

void loop() {
  static bool lastState = HIGH;
  static unsigned long pressTime = 0;
  bool currState = digitalRead(BUTTON_PIN);
  
  if (!bleConnected) {
    central = BLE.central();
    if (central) {
      // display
      u8x8.clear();
      u8x8.setCursor(0, 0);
      u8x8.print("BLE Connected!");

      Serial.print("Connected to central: ");
      Serial.println(central.address());
      bleConnected = true;
    }
  } else if (!central.connected()) {
    // display
    u8x8.clear();
    u8x8.setCursor(0, 0);
    u8x8.print("BLE Disconnected!");

    Serial.println("Disconnected from central");
    bleConnected = false;

    BLE.advertise();
    Serial.println("BLE advertising started...")
  }

  // Detecting Button Press
  if (lastState == HIGH && currState == LOW) {
    pressTime = millis(); // starting the button press
  }

  if (lastState == LOW && currState == HIGH) {
    unsigned long duration = millis() - pressTime;  // when the button is released
    if (duration < 500) {
      // press button once and it will take a pictre
      takePicture();
    } else {
      // letting go of the button will make it stop recording
      isRecording = false;
    }
  }

  if (currState == LOW && (millis() - pressTime >= 500) && !isRecording) {
    // holding the button will record the audio to be transcribed
    isRecording = true;
    Serial.println("Button held: Start recording");
    u8x8.clear();
    u8x8.setCursor(0, 0);
    u8x8.print("Recording...");
    xTaskCreatePinnedToCore(i2s_adc, "i2s_adc", 1024 * 8, NULL, 1, &recordTaskHandle, 0); // task to connect microphone and start recording
  }

  lastState = currState;
  delay(10);
}

void takePicture() {
  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Capture failed");
    return;
  }
  String filename = "/photo_" + String(millis()) + ".jpg";
  File file = SD.open(filename, FILE_WRITE);
  if (file) {
    file.write(fb->buf, fb->len);
    file.close();
    Serial.println("Photo saved as: " + filename);
    u8x8.clear(); u8x8.setCursor(0, 0); u8x8.print("Photo taken");
  }
  esp_camera_fb_return(fb);
}

void i2s_adc(void *arg) {
  // Number of bytes required for the recording buffer
  const uint32_t max_size = SAMPLE_RATE * SAMPLE_BITS / 8 * 240;

  // This code uses the ESP32's PSRAM (external cache memory) to dynamically allocate a section of memory to store the recording data.
  uint8_t *rec_buffer = (uint8_t *)ps_malloc(max_size);
  if (!rec_buffer) {
    Serial.println("ps_malloc failed");
    vTaskDelete(NULL);
  }

  // Delete existing file if it exists
  if (SD.exists(AUDIO_FILE)) {
    SD.remove(AUDIO_FILE);
  }

  File file = SD.open(AUDIO_FILE, FILE_WRITE);

  if (!file) {
    Serial.println("Failed to open file");
    free(rec_buffer);
    vTaskDelete(NULL);
  }

  // Write the header to the WAV file
  uint8_t wav_header[WAV_HEADER_SIZE];

  // Write the WAV file header information to the wav_header array
  generate_wav_header(wav_header, max_size, SAMPLE_RATE);

  // Call the file.write() function to write the data in the wav_header array to the newly created WAV file
  file.write(wav_header, WAV_HEADER_SIZE);

  // Turn ON LED to indicate recording started
  digitalWrite(LED_BUILTIN, LOW);
  uint32_t written = 0;

  // Start recording
  // I2S port number (in this case I2S_NUM_0), 
  // a pointer to the buffer to which the data is to be written (i.e. rec_buffer),
  // the size of the data to be read (i.e. record_size),
  // a pointer to a variable that points to the actual size of the data being read (i.e. &sample_size),
  // and the maximum time to wait for the data to be read (in this case portMAX_DELAY, indicating an infinite wait time).
  while (isRecording && written < max_size) {
    int bytesRead = I2S.readBytes((char *)rec_buffer + written, 512);
    written += bytesRead;
  }

  // Increase volume
  for (uint32_t i = 0; i < written; i += 2) {
    (*(uint16_t *)(rec_buffer + i)) <<= VOLUME_GAIN;
  }

  // Write data to the WAV file
  file.seek(WAV_HEADER_SIZE);
  file.write(rec_buffer, written);
  file.close();

  // Turn OFF LED to indicate recording finished
  digitalWrite(LED_BUILTIN, HIGH);

  Serial.printf("Recorded %d bytes\n", written);

  if (isWIFIConnected) {
    // display
    u8x8.clear();
    u8x8.setCursor(0, 0);
    u8x8.print("Transcribing...");

    String transcription = SpeechToText_Deepgram(AUDIO_FILE);
    Serial.println("Deepgram says: " + transcription);

    if (bleConnected) {
      sendTextData(transcription.c_str());
    }

    // display text on screen and wraps it around to the next line
    u8x8.clear();
    u8x8.setCursor(0, 0);
    int len = transcription.length();
    for (int i = 0, row = 0; i < len && row < 8; i += 16, row++) {
      u8x8.setCursor(0, row);
      u8x8.print(transcription.substring(i, i + 16));
    }
  }

  free(rec_buffer);
  recordTaskHandle = NULL;
  vTaskDelete(NULL);
}

void sendTextData(const char* speechText) {
  size_t len = strlen(speechText);

  if (len > CHARACTERISTIC_SIZE) {
    len = CHARACTERISTIC_SIZE;  // Truncate to fit characteristic size
  }

  txCharacteristic.writeValue((const uint8_t*)speechText, len);
  Serial.print("Sent over BLE: ");
  Serial.println(speechText);
}

void generate_wav_header(uint8_t *wav_header, uint32_t wav_size, uint32_t sample_rate) {
  // See this for reference: http://soundfile.sapp.org/doc/WaveFormat/
  uint32_t file_size = wav_size + WAV_HEADER_SIZE - 8;
  uint32_t byte_rate = sample_rate * SAMPLE_BITS / 8;
  const uint8_t set_wav_header[] = {
    'R','I','F','F', // ChunkID
    file_size, file_size >> 8, file_size >> 16, file_size >> 24, // ChunkSize
    'W','A','V','E', // Format
    'f','m','t',' ', // Subchunk1ID
    0x10,0x00,0x00,0x00, // Subchunk1Size (16 for PCM)
    0x01,0x00, // AudioFormat (1 for PCM)
    0x01,0x00, // NumChannels (1 channel)
    sample_rate, sample_rate >> 8, sample_rate >> 16, sample_rate >> 24, // SampleRate
    byte_rate, byte_rate >> 8, byte_rate >> 16, byte_rate >> 24, // ByteRate
    0x02,0x00, // BlockAlign 
    0x10,0x00, // BitsPerSample (16 bits)
    'd','a','t','a', // Subchunk2ID
    wav_size, wav_size >> 8, wav_size >> 16, wav_size >> 24 // Subchunk2Size
  };
  memcpy(wav_header, set_wav_header, sizeof(set_wav_header));
}

void wifiConnect(void *pvParameters) {
  isWIFIConnected = false;
  char* ssid = "ur mom";
  char* password = "Giveme$500bucks";

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    vTaskDelay(500);
    Serial.print(".");
  }
  u8x8.clear();
  u8x8.setCursor(0, 0);
  u8x8.print("Wi-Fi Connected!");
  Serial.println("Wi-Fi Connected!");
  isWIFIConnected = true;
  while (true) {
    vTaskDelay(1000);
  }
}

