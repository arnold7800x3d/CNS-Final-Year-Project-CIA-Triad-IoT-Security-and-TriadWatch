/*
  this is the source code for the esp32 38-pin functionality of the bank monitoring system. it features:
    - esp32 38pin   - microcontroller
    - dht11 module  - temperature and humidity readings
    - OLED display  - display
    - LED           - status indicators
    - PIR           - detect motion
    - buzzer        - alarm indicator
*/

// import necessary libraries
#include "DHT.h"  // DHT11 library
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <mbedtls/aes.h> // AES-256 encryption library
#include "mbedtls/sha256.h" // SHA-256 encryption
#include "arduino_base64.hpp" // base64 encoding library

// variables
#define DHTPIN 0  // pin for the DHT module
#define DHTTYPE DHT11

// OLED display configuration
#define SCREEN_WIDTH 128  
#define SCREEN_HEIGHT 64  
#define OLED_RESET -1
#define SCREEN_ADDRESS 0X3C

const int ledPin = 2;  // pin for the LED

// objects
DHT dht(DHTPIN, DHTTYPE);
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// 32-byte AES-256 key (DO NOT randomly generate this each time!)
byte aesKey[] = {
  21, 42, 63, 84, 105, 126, 147, 168,
  189, 210, 231, 252, 17, 34, 51, 68,
  85, 102, 119, 136, 153, 170, 187, 204,
  221, 238, 255, 1, 18, 35, 52, 69
};

String encryptSensorData(String inputData) {
  // 1. Convert input string to byte array
  int inputDataLength = inputData.length();
  byte plaintext[inputDataLength];
  inputData.getBytes(plaintext, inputDataLength);

  // 2. Apply manual PKCS7 padding
  int padding = 16 - (inputDataLength % 16);
  int paddedLength = inputDataLength + padding;
  byte paddedPlaintext[paddedLength];
  memcpy(paddedPlaintext, plaintext, inputDataLength);
  memset(paddedPlaintext + inputDataLength, padding, padding);

  // 3. Generate random IV
  byte randomIV[16];
  for (int i = 0; i < 16; i++) {
    randomIV[i] = esp_random() % 256;
  }

  // 4. Prepare output buffer
  byte encryptedData[paddedLength];

  // 5. AES encryption
  mbedtls_aes_context aes;
  mbedtls_aes_init(&aes);
  mbedtls_aes_setkey_enc(&aes, aesKey, 256);
  mbedtls_aes_crypt_cbc(&aes, MBEDTLS_AES_ENCRYPT, paddedLength, randomIV, paddedPlaintext, encryptedData);
  mbedtls_aes_free(&aes);

  // 6. Concatenate IV + ciphertext
  int totalLength = 16 + paddedLength;
  byte finalOutput[totalLength];
  memcpy(finalOutput, randomIV, 16);
  memcpy(finalOutput + 16, encryptedData, paddedLength);

  // 7. Base64 encode IV + ciphertext
  char base64EncodedOutput[base64::encodeLength(totalLength)];
  base64::encode(finalOutput, totalLength, base64EncodedOutput);

  return String(base64EncodedOutput);
}

String hashSensorData(String input) {
  byte SHAResult[32]; // 32 bytes bytes for SHA-256 output
  mbedtls_sha256_context ctx;

  mbedtls_sha256_init(&ctx);
  mbedtls_sha256_starts(&ctx, 0); // 0 for SHA-256
  mbedtls_sha256_update(&ctx, (const unsigned char*)input.c_str(), input.length());
  mbedtls_sha256_finish(&ctx, SHAResult);
  mbedtls_sha256_free(&ctx);

  // conversion to hexadecimal
  String hashString = "";
  for (int i = 0; i < 32; i++) {
    if (SHAResult[i] < 16) hashString += "0";
    hashString += String(SHAResult[i], HEX);
  }

  return hashString;
}

void setup() {
  Serial.begin(9600);
  
  // initialization of the OLED display
  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {  // Address 0x3D for 128x64
    Serial.println(F("SSD1306 allocation failed"));
    for (;;)
      ;
  }

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Initializing display");
  display.display();
  delay(2000);

  pinMode(ledPin, OUTPUT);
  dht.begin(); // initialize the dht11 module
}

void loop() {
  // oled display
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(WHITE);
  display.setCursor(0, 0);

  // dht11 module functionality
  float humidity = dht.readHumidity(); // variable for humidity
  float temperature = dht.readTemperature(); // variable for temperature

  if (isnan(humidity) || isnan(temperature)) {
    Serial.println("Failed to read from DHT sensor!"); // display if dht module reads an invalid value
    display.print("DHT11 module error");
  } else {
    String payload = "Temp:" + String(temperature, 1) + "C, Hum:" + String(humidity, 1) + "%"; // plaintext
    String encryptedPayload = encryptSensorData(payload); // encrypt plaintext
    String hashOutput = hashSensorData(payload);

    display.println("Plaintext:"); 
    display.println(payload); // display plaintext on oled dislay
    display.println("Ciphertext");  
    display.println(encryptedPayload); // display ciphertext on oled display
    Serial.print("Payload: ");
    Serial.println(payload);
    Serial.print("Encrypted(Base64): ");
    Serial.println(encryptedPayload);
    Serial.print("SHA-256: ");
    Serial.println(hashOutput);

    //Serial.print("Humidity: "); Serial.print(humidity); Serial.println("%"); // display humidity values on the serial monitor
    //display.print("Humidity: "); display.print(humidity); display.println("%"); // display humidity values on the oled display
    //Serial.print("Temperature: "); Serial.print(temperature); Serial.println("°C"); // display temperature values on the serial monitor
    //display.print("Temperature: "); display.print(temperature); display.println("°C"); // display temperature values on the oled display
  }

  display.display();
  delay(3000);
}
