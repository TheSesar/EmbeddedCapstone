#include <ArduinoBLE.h>


// UUIDs for Smart Glasses BLE Application
const char* serviceUUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
const char* txCharacteristicUUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; // MCU to Android
const char* rxCharacteristicUUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; // Android to MCU
const char* descriptorUUID = "2901";  // Standard descriptor UUID

#define CHARACTERISTIC_SIZE 4  // Adjust as needed for your sensor data packet size

BLEService customService(serviceUUID);
BLECharacteristic txCharacteristic(characteristicUUID,
                                   BLERead | BLENotify,
                                   CHARACTERISTIC_SIZE);
BLEDescriptor myDescriptor(descriptorUUID, "Sensor Data");

void setup() {
  Serial.begin(9600);
  while (!Serial);

  if (!BLE.begin()) {
    Serial.println("Starting BLE failed!");
    while (1);
  }

  BLE.setLocalName("SmartGlassesMCU");
  BLE.setAdvertisedService(customService);

  customService.addCharacteristic(txCharacteristic);
  txCharacteristic.addDescriptor(myDescriptor);

  BLE.addService(customService);
  BLE.advertise();

  Serial.println("BLE device is now advertising");
}

void loop() {
  BLEDevice central = BLE.central();

  if (central) {
    Serial.print("Connected to central: ");
    Serial.println(central.address());

    while (central.connected()) {
      sendSensorData();
      delay(1000);  // Send data every second
    }

    handleDisconnection();
  }
}

void sendSensorData() {
  uint8_t packet[CHARACTERISTIC_SIZE];

  // Example dummy sensor data (e.g., incrementing counter or random values)
  static uint32_t counter = 0;
  packet[0] = (counter >> 24) & 0xFF;
  packet[1] = (counter >> 16) & 0xFF;
  packet[2] = (counter >> 8) & 0xFF;
  packet[3] = counter & 0xFF;

  txCharacteristic.writeValue(packet, CHARACTERISTIC_SIZE);
  Serial.print("Sent packet: ");
  Serial.println(counter);
  counter++;
}

void handleDisconnection() {
  Serial.println("Central disconnected");
}
