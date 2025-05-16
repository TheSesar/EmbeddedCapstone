#include <ArduinoBLE.h>
#include <SPI.h>

#define CHARACTERISTIC_SIZE 512

const char* serviceUUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";      // Custom service UUID
const char* rxCharUUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";  // Metadata characteristic UUID (read/notify)
const char* txCharUUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";        // Image/data characteristic UUID (write/notify)

BLEService myService(serviceUUID);
BLECharacteristic rxCharacteristic(rxCharUUID, BLERead | BLENotify, CHARACTERISTIC_SIZE);
BLECharacteristic txCharacteristic(txCharUUID, BLERead | BLEWrite | BLENotify, CHARACTERISTIC_SIZE);

BLEDescriptor myDescriptor("00002902-0000-1000-8000-00805f9b34fb", "0");

void setup() {
  Serial.begin(115200);
  while (!Serial);

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
}


void loop() {
  BLEDevice central = BLE.central();

  if (central) {
    Serial.print("Connected to central: ");
    Serial.println(central.address());

    while (central.connected()) {
      const char* text = "Translated Conversation!";
      sendTextData(text);
      delay(500);  // Delay to avoid flooding the BLE connection
    } 
    Serial.println("Disconnected from central");
  }
}

void sendTextData(const char* speechText) {
  size_t len = strlen(speechText);

  if (len > CHARACTERISTIC_SIZE) {
    len = CHARACTERISTIC_SIZE; // Truncate to fit characteristic size
  }

  txCharacteristic.writeValue((const uint8_t*)speechText, len);
  Serial.print("Sent text: ");
  Serial.println(speechText);
}


void handleDisconnection() {
  Serial.println("Central disconnected");
}
