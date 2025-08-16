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
#include <mbedtls/aes.h>       // AES-256 encryption library
#include "mbedtls/sha256.h"    // SHA-256 encryption
#include "arduino_base64.hpp"  // base64 encoding library
#include "secrets.h"

// WiFi transmission and Firebase
#include <WiFi.h>
#include "time.h"
#include <Firebase_ESP_Client.h>

// variables
#define DHTPIN 0  // pin for the DHT module
#define DHTTYPE DHT11

// OLED display configuration
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define SCREEN_ADDRESS 0X3C

#define LED_DB_PATH "/triadwatch/commands/espLed"

const int ledPin = 2;  // pin for the LED

// time settings
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 3 * 3600;  // Adjust for your timezone (e.g., GMT+3)
const int daylightOffset_sec = 0;

time_t bootTime;  // store the actual UTC time at boot

// objects
DHT dht(DHTPIN, DHTTYPE);
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
FirebaseData fbData;
FirebaseAuth auth;
FirebaseConfig config;
FirebaseData fbLedData;

void sendSensorToFirebase(String type, String encrypted, String hash) {
  String path = "/bank_monitoring/sensors/" + type;

  // Compute current UTC timestamp
  time_t timestamp = bootTime + millis() / 1000;

  // Create JSON object for this reading
  FirebaseJson json;
  json.set("encrypted", encrypted);
  json.set("hash", hash);
  json.set("timestamp", timestamp);

  // Push JSON object as a single reading
  if (Firebase.RTDB.pushJSON(&fbData, path, &json)) {
    // Update "latest" snapshot
    Firebase.RTDB.setString(&fbData, path + "/latest", encrypted);
    Firebase.RTDB.setString(&fbData, path + "/hash", hash);
    Firebase.RTDB.setInt(&fbData, path + "/timestamp", timestamp);
  } else {
    Serial.print("Firebase push failed: ");
    Serial.println(fbData.errorReason());
  }
}


// 32-byte AES-256 key (DO NOT randomly generate this each time!)
byte aesKey[] = {
  21, 42, 63, 84, 105, 126, 147, 168,
  189, 210, 231, 252, 17, 34, 51, 68,
  85, 102, 119, 136, 153, 170, 187, 204,
  221, 238, 255, 1, 18, 35, 52, 69
};

// Timer for sensor reads
unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL = 5000;  // 3 seconds

String encryptSensorData(String inputData) {
  const byte* plaintext = (const byte*)inputData.c_str();
  int inputDataLength = inputData.length();

  int padding = 16 - (inputDataLength % 16);
  int paddedLength = inputDataLength + padding;
  byte paddedPlaintext[paddedLength];
  memcpy(paddedPlaintext, plaintext, inputDataLength);
  memset(paddedPlaintext + inputDataLength, padding, padding);

  byte randomIV[16];
  for (int i = 0; i < 16; i++) randomIV[i] = esp_random() % 256;

  byte encryptedData[paddedLength];
  mbedtls_aes_context aes;
  mbedtls_aes_init(&aes);
  mbedtls_aes_setkey_enc(&aes, aesKey, 256);
  byte ivCopy[16];
  memcpy(ivCopy, randomIV, 16);
  mbedtls_aes_crypt_cbc(&aes, MBEDTLS_AES_ENCRYPT, paddedLength, ivCopy, paddedPlaintext, encryptedData);
  mbedtls_aes_free(&aes);

  int totalLength = 16 + paddedLength;
  byte finalOutput[totalLength];
  memcpy(finalOutput, randomIV, 16);
  memcpy(finalOutput + 16, encryptedData, paddedLength);

  char base64EncodedOutput[base64::encodeLength(totalLength) + 1];
  base64::encode(finalOutput, totalLength, base64EncodedOutput);
  base64EncodedOutput[base64::encodeLength(totalLength)] = '\0';

  // Debug logs
  Serial.print("IV length: ");
  Serial.println(16);
  Serial.print("Ciphertext length: ");
  Serial.println(paddedLength);
  Serial.print("Total output length: ");
  Serial.println(totalLength);
  Serial.print("Base64 length: ");
  Serial.println(strlen(base64EncodedOutput));

  return String(base64EncodedOutput);
}


String hashSensorData(String input) {
  byte SHAResult[32];  // 32 bytes bytes for SHA-256 output
  mbedtls_sha256_context ctx;

  mbedtls_sha256_init(&ctx);
  mbedtls_sha256_starts(&ctx, 0);  // 0 for SHA-256
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

  pinMode(ledPin, OUTPUT);
  digitalWrite(ledPin, LOW);

  //Firebase.RTDB.beginStream(&fbData, "/bank_monitoring/actuators/led/state");

  // Connect to Wi-Fi
  WiFi.begin(SECRET_SSID, SECRET_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("Connected!");

  // Initialize NTP
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  Serial.println("Fetching NTP time...");
  struct tm timeinfo;
  while (!getLocalTime(&timeinfo)) {
    Serial.print(".");
    delay(500);
  }

  // Store boot time as reference
  bootTime = time(nullptr);
  Serial.print("Boot UTC time: ");
  Serial.println(bootTime);

  // Firebase config
  config.api_key = SECRET_API_KEY;
  config.database_url = SECRET_DATABASE_URL;

  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // Start Firebase stream for LED
  if (!Firebase.RTDB.beginStream(&fbLedData, LED_DB_PATH)) {
    Serial.println("Failed to begin stream for LED:");
    Serial.println(fbLedData.errorReason());
  }

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

  dht.begin();  // initialize the dht11 module
}

void loop() {
  if (Firebase.RTDB.readStream(&fbLedData)) {
    if (fbLedData.streamAvailable()) {
      String ledState = fbLedData.stringData();
      if (ledState == "ON") {
        digitalWrite(ledPin, HIGH);
      } else {
        digitalWrite(ledPin, LOW);
      }
    }
  }

  // Clear OLED and set cursor
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(WHITE);
  display.setCursor(0, 0);

  // Read DHT11 sensor
  float humidity = dht.readHumidity();
  float temperature = dht.readTemperature();
  if (isnan(humidity) || isnan(temperature)) {
    Serial.println("Failed to read from DHT sensor!");
    display.print("DHT11 module error");
  } else {

    // Prepare payload strings
    String tempPayload = "Temp:" + String(temperature, 1) + "°C";
    String humidityPayload = "Humidity:" + String(humidity, 1) + "%";

    // Encrypt
    String tempEncrypted = encryptSensorData(tempPayload);
    String humidityEncrypted = encryptSensorData(humidityPayload);

    // Hash
    String tempHash = hashSensorData(tempPayload);
    String humidityHash = hashSensorData(humidityPayload);

    // Log to Firebase with proper history
    sendSensorToFirebase("temperature", tempEncrypted, tempHash);
    sendSensorToFirebase("humidity", humidityEncrypted, humidityHash);

    // Display on OLED
    String combinedPayload = tempPayload + ", " + humidityPayload;
    display.println("Plaintext:");
    display.println(combinedPayload);
    display.println("Ciphertext (Temp):");
    display.println(tempEncrypted);
    display.println("Ciphertext (Hum):");
    display.println(humidityEncrypted);

    // Serial output
    Serial.print("Payload: ");
    Serial.println(combinedPayload);
    Serial.print("Encrypted Temp: ");
    Serial.println(tempEncrypted);
    Serial.print("SHA-256 Temp: ");
    Serial.println(tempHash);
    Serial.print("Encrypted Hum: ");
    Serial.println(humidityEncrypted);
    Serial.print("SHA-256 Hum: ");
    Serial.println(humidityHash);
  }
  display.display();
  delay(3000);
}