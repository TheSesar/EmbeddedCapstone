# 🕶️ Smart Glasses – Embedded Systems Capstone Project

The Smart Glasses project is a wearable, embedded system that enables **real-time spoken language translation** using a combination of speech recognition, cloud-based translation, Bluetooth communication, and audio playback. Developed as part of the University of Washington's **EE/CSE 475 Embedded Systems Capstone**, this project demonstrates how embedded devices and mobile platforms can work together to create a seamless, multilingual communication experience for users.

Powered by the **Seeed Studio XIAO ESP32S3** microcontroller, the Smart Glasses capture a speaker’s voice, convert it into text using real-time speech-to-text (STT) via **Deepgram's API**, transmit the transcribed text over **Bluetooth Low Energy (BLE)** to a custom-built Android application, and use **Google Cloud Translation API** to convert the speech into a target language. The translated text is then sent back to the glasses, where it is spoken aloud using an **I2S audio playback system**, completing the end-to-end translation loop entirely through embedded and mobile technologies.

This project highlights real-time interaction across wireless protocols, on-device processing, and cross-platform integration—all designed to fit within a compact, wearable device aimed at increasing accessibility and global communication.

---

### 🧠 System Capabilities

- **Speech-to-Text (STT)**: The ESP32S3 captures live audio through its onboard microphone. Using the built-in APU (Audio Processing Unit), it streams audio to the **Deepgram real-time transcription API** over Wi-Fi. This allows on-device audio input to be transcribed without involving the Android app, reducing latency and increasing modularity.
  
- **Wireless BLE Communication**: The transcribed text is transmitted from the ESP32S3 to an Android device using **Bluetooth Low Energy (BLE)**. The ESP32S3 acts as a BLE peripheral device with custom **GATT characteristics**, allowing the Android app to subscribe to transcribed text data using **notifications** and respond with translations via **write requests**.

- **Cloud-based Translation**: Upon receiving the transcribed text, the Android app utilizes the **Google Cloud Translation API** to detect the source language and convert it into a user-specified target language. This happens nearly instantly and supports a wide array of languages, making the system globally adaptable.

- **Text-to-Speech Playback**: After translation, the app sends the translated text back to the ESP32S3 over BLE. The ESP32 then converts this text into spoken audio using its onboard **I2S audio driver** connected to a DAC module or amplifier. This final step completes the live language translation cycle and ensures full auditory feedback for the user.

### 🔧 Engineering Focus

The Smart Glasses project combines multiple disciplines:
- **Embedded systems design** using C/C++ and Arduino for microcontroller programming
- **Wireless protocols** with BLE GATT profile design for reliable data transfer
- **Cloud service integration** with REST APIs for STT and translation
- **Android application development** in Java for BLE connectivity, HTTP requests, and UI display
- **Real-time data processing and audio output** for seamless user interaction

The architecture emphasizes **low latency**, **power efficiency**, and **ease of use**, targeting real-world applications like travel, multilingual accessibility, and communication for hearing-impaired users.

---

## 📦 Repository Structure
The project is organized into the following directories and files:

SmartGlasses/
├── ESP32S3_BLECode_ino/ # Arduino firmware for the Seeed Studio XIAO ESP32S3
│ ├── SmartGlasses.ino # Main logic: BLE, speech-to-text, I2S audio output
│ └── ... # Supporting modules for audio and BLE handling
│
├── SmartGlassesAndroidApp/ # Android app (Java) for BLE, translation, and UI
│ ├── app/ # Main Android app source files
│ ├── gradle/ # Gradle wrapper and build configuration
│ ├── build.gradle # App-level dependencies and settings
│ ├── settings.gradle # Project configuration
│ └── ...
│
├── TranslatorApp2/ # Experimental or alternate version of the translator app
│
├── .idea/ # Android Studio project settings
├── .gitignore # Files/directories to exclude from Git
├── .DS_Store # System file (ignored)
├── README.md # 📘 Project documentation (this file)


---

## 🔧 System Overview

### 🎛️ Hardware: Seeed Studio XIAO ESP32S3
- Onboard microphone for live audio input
- Uses **Audio Processing Unit (APU)** for low-power audio capture
- Speech-to-text handled on-device via **Deepgram streaming API**
- BLE communication using **custom GATT service**
- I2S DAC output for text-to-speech playback

### 📱 Software: Android App (Java)
- Scans for and connects to BLE devices
- Subscribes to GATT characteristic to receive text
- Translates text using **Google Cloud Translation API**
- Sends translated text back to ESP32-S3 for audio output

---

## 🔄 Data Flow

1. **ESP32-S3** records audio and streams it to Deepgram via Wi-Fi.
2. Deepgram returns transcribed text to the ESP32.
3. Transcribed text is sent over BLE using **GATT notifications**.
4. Android app receives the text and sends it to **Google Cloud Translation API**.
5. Translated text is sent back to ESP32 using **GATT writes**.
6. ESP32 plays the translated text via **I2S audio output**.

---

## 🧩 Key Features

### 🔊 Speech-to-Text (Embedded)
- On-device recording and streaming to Deepgram
- Efficient use of ESP32-S3’s APU for audio acquisition
- No reliance on Android-side audio processing

### 📡 Bluetooth Low Energy (BLE)
- ESP32-S3 acts as BLE **peripheral**
- Android app scans and connects as **central**
- **GATT characteristic** used to:
  - Notify Android app with transcribed text
  - Receive translated text from Android app

### 🌍 Real-Time Translation
- Uses **Google Cloud Translation API**
- Supports translation between multiple languages
- Fast round-trip from speech to translated audio playback

### 🗣️ Audio Playback
- ESP32-S3 outputs speech via **I2S DAC**
- Provides natural feedback to user after translation

---

## 📲 Android App Setup

1. Open `SmartGlassesAndroidApp` in **Android Studio**.
2. Enable **Bluetooth** and **Location Services** on your Android device.
3. Build and run the app on a physical device (BLE required).
4. Use the app to connect to ESP32-S3, receive and translate text, and send it back.

---

## 🤖 ESP32 Firmware Setup

1. Open `ESP32S3_BLECode_ino` in **Arduino IDE** or **PlatformIO**.
2. Install required libraries:
   - `WiFi.h`
   - `BLEDevice.h`
   - `HTTPClient.h`
   - `I2S.h`
3. Add your **Wi-Fi credentials** and **Deepgram API key** to the sketch.
4. Flash the firmware to the **Seeed Studio XIAO ESP32S3**.
5. Monitor serial output for debugging BLE or audio events.

---

## 🔐 API Keys & Config

| Service       | Setup Required                     |
|---------------|-------------------------------------|
| Deepgram API  | [Get API Key](https://deepgram.com/) and add to ESP32 code |
| Google Cloud Translation | [Enable Translation API](https://cloud.google.com/translate) and add API key to Android app |

---

## 🚀 Future Work

- Add **wake-word detection** (offline trigger)
- Implement **multilingual mode switching**
- Improve **BLE throughput and buffering**
- Support **offline translation models**
- Add **UI enhancements** for app readability

---

## 👨‍💻 Authors & Acknowledgments

- Course: EE/CSE 475 – Embedded Systems, University of Washington
- Special thanks to:
  - **Deepgram** – Real-time speech-to-text
  - **Google Cloud** – Translation API
  - **Seeed Studio** – ESP32-S3 microcontroller and support
