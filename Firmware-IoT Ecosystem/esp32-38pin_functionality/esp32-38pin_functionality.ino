/*
  this is the source code for the esp32 38-pin functionality of the bank monitoring system. it features:
    - esp32 38pin               - microcontroller
    - dht11 module              - temperature and humidity readings
    - OLED display              - display
    - LED                       - status indicators
    - PIR                       - detect motion
    - buzzer                    - alarm indicator
    - light dependent resistor  - detect light based tampering
    - ultrasonic sensor         - detect approaching objects
*/

// import necessary libraries
#include "DHT.h"  // DHT11 library
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <mbedtls/aes.h>       // AES-256 encryption library
#include "mbedtls/sha256.h"    // SHA-256 encryption
#include "arduino_base64.hpp"  // base64 encoding library
#include "secrets.h" // file containing WiFi credentials

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
#define SCREEN_ADDRESS 0x3C

// LED command paths
#define BLUE_LED_DB_PATH "/triadwatch/commands/blueLED" 
#define WHITE_LED_DB_PATH "triadwatch/commands/whiteLED"

// pin configurations
const int blueLEDPin = 25;  
const int whiteLEDPin = 26;
const int buzzerPin = 19;
const int ldrPin = 34;
const int trigPin = 18;
const int echoPin = 5;
long duration, distance;

// time settings
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 3 * 3600;  // (GMT + 3)
const int daylightOffset_sec = 0;

time_t bootTime;  // store the actual UTC time at boot

// objects
DHT dht(DHTPIN, DHTTYPE);
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
FirebaseData fbData;
FirebaseAuth auth;
FirebaseConfig config;
FirebaseData fbBlueLEDData;
FirebaseData fbWhiteLEDData;

// function to upload sensor data to Firebase Realtime DB
void sendSensorToFirebase(String type, String encrypted, String hash) {
  String path = "/bank_monitoring/sensors/" + type;

  // compute current UTC timestamp
  time_t timestamp = bootTime + millis() / 1000;

  // create JSON object sensor reading
  FirebaseJson json;
  json.set("encrypted", encrypted);
  json.set("hash", hash);
  json.set("timestamp", timestamp);

  // push JSON object as a single reading
  if (Firebase.RTDB.pushJSON(&fbData, path, &json)) {
    // update "latest" snapshot of the sensor data which will be shown in the mobile application
    Firebase.RTDB.setString(&fbData, path + "/latest", encrypted);
    Firebase.RTDB.setString(&fbData, path + "/hash", hash);
    Firebase.RTDB.setInt(&fbData, path + "/timestamp", timestamp);
  } else {
    Serial.print("Firebase push failed: ");
    Serial.println(fbData.errorReason());
  }
}


// 32-byte AES-256 key (developer defined)
byte aesKey[] = {
  21, 42, 63, 84, 105, 126, 147, 168,
  189, 210, 231, 252, 17, 34, 51, 68,
  85, 102, 119, 136, 153, 170, 187, 204,
  221, 238, 255, 1, 18, 35, 52, 69
};

// Timer for sensor reads to implement non-blocking to reduce LED toggle delay
unsigned long lastSensorRead = 0;
const unsigned long SENSOR_INTERVAL = 3000;  // 3 seconds

// function to encrypt the sensor data using AES-256
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

  // serial output for debugging
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

// function to hash the sensor data using SHA-256
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

  // pin setup
  pinMode(blueLEDPin, OUTPUT);
  pinMode(whiteLEDPin, OUTPUT);
  digitalWrite(blueLEDPin, LOW);
  digitalWrite(whiteLEDPin, LOW);
  pinMode(ldrPin, INPUT);
  pinMode(buzzerPin, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  // connect to Wi-Fi
  WiFi.begin(SECRET_SSID, SECRET_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("Connected!");

  // initialize NTP to get the current time information
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  Serial.println("Fetching NTP time...");
  struct tm timeinfo;
  while (!getLocalTime(&timeinfo)) {
    Serial.print(".");
    delay(500);
  }

  // store the ESP boot time as reference
  bootTime = time(nullptr);
  Serial.print("Boot UTC time: ");
  Serial.println(bootTime);

  // firebase initialization and config
  config.api_key = SECRET_API_KEY;
  config.database_url = SECRET_DATABASE_URL;

  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // begin Firebase stream for the LEDs
  if (!Firebase.RTDB.beginStream(&fbBlueLEDData, BLUE_LED_DB_PATH)) {
    Serial.println("Failed to begin stream for Blue LED:");
    Serial.println(fbBlueLEDData.errorReason());
  }

  if (!Firebase.RTDB.beginStream(&fbWhiteLEDData, WHITE_LED_DB_PATH)) {
    Serial.println("Failed to begin stream for White LED:");
    Serial.println(fbWhiteLEDData.errorReason());
  }
  // initialization of the OLED display
  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {  
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
    if (Firebase.RTDB.readStream(&fbBlueLEDData)) { // check stream for blue LED
    if (fbBlueLEDData.streamAvailable()) {
      Serial.println("--- Blue LED Stream Event ---"); // differentiate logs
      Serial.print("Path: "); Serial.println(fbBlueLEDData.dataPath());
      Serial.print("Type: "); Serial.println(fbBlueLEDData.eventType());

      // expecting a boolean value from the mobile app for the LED state
      if (fbBlueLEDData.dataTypeEnum() == fb_esp_rtdb_data_type_boolean) {
        bool blueLEDIsOn = fbBlueLEDData.boolData();  // read value as boolean
        Serial.print("Received Blue LED state (boolean): ");
        Serial.println(blueLEDIsOn ? "true (ON)" : "false (OFF)");

        // blue LED toggle
        if (blueLEDIsOn) {
          digitalWrite(blueLEDPin, HIGH);
          Serial.println("Blue LED Turned ON");
        } else {
          digitalWrite(blueLEDPin, LOW);  
          Serial.println("Blue LED Turned OFF");
        }
      }
      // fallback for string data for Blue LED
      else if (fbBlueLEDData.dataTypeEnum() == fb_esp_rtdb_data_type_string) {
        String blueLEDStateStr = fbBlueLEDData.stringData();
        Serial.print("Received Blue LED state (string - manual test?): ");
        Serial.println(blueLEDStateStr);
        if (blueLEDStateStr.equalsIgnoreCase("true") || blueLEDStateStr.equalsIgnoreCase("on")) {
          digitalWrite(blueLEDPin, HIGH);
          Serial.println("Blue LED Turned ON (from string)");
        } else if (blueLEDStateStr.equalsIgnoreCase("false") || blueLEDStateStr.equalsIgnoreCase("off")) {
          digitalWrite(blueLEDPin, LOW);  
          Serial.println("Blue LED Turned OFF (from string)");
        } else {
          Serial.print("Unknown string value for Blue LED state: "); Serial.println(blueLEDStateStr);
        }
      } else {
        Serial.print("Unexpected data type for Blue LED state: ");
        Serial.println(fbBlueLEDData.dataType());
        Serial.print("Payload: "); Serial.println(fbBlueLEDData.payload());
      }
    }
  }
 
  // white LED control
  if (Firebase.RTDB.readStream(&fbWhiteLEDData)) { // check stream for white LED
    if (fbWhiteLEDData.streamAvailable()) {
      Serial.println("--- White LED Stream Event ---"); 
      Serial.print("Path: "); Serial.println(fbWhiteLEDData.dataPath());
      Serial.print("Type: "); Serial.println(fbWhiteLEDData.eventType());

      // expecting a boolean value from the mobile app for the LED state
      if (fbWhiteLEDData.dataTypeEnum() == fb_esp_rtdb_data_type_boolean) {
        bool whiteLEDIsOn = fbWhiteLEDData.boolData();  // read value as boolean
        Serial.print("Received White LED state (boolean): ");
        Serial.println(whiteLEDIsOn ? "true (ON)" : "false (OFF)");

        // white LED toggle
        if (whiteLEDIsOn) {
          digitalWrite(whiteLEDPin, HIGH); 
          Serial.println("White LED Turned ON");
        } else {
          digitalWrite(whiteLEDPin, LOW);  
          Serial.println("White LED Turned OFF");
        }
      }
      // fallback for string data for white LED
      else if (fbWhiteLEDData.dataTypeEnum() == fb_esp_rtdb_data_type_string) {
        String whiteLEDStateStr = fbWhiteLEDData.stringData();
        Serial.print("Received White LED state (string - manual test?): ");
        Serial.println(whiteLEDStateStr);
        if (whiteLEDStateStr.equalsIgnoreCase("true") || whiteLEDStateStr.equalsIgnoreCase("on")) {
          digitalWrite(whiteLEDPin, HIGH); 
          Serial.println("White LED Turned ON (from string)");
        } else if (whiteLEDStateStr.equalsIgnoreCase("false") || whiteLEDStateStr.equalsIgnoreCase("off")) {
          digitalWrite(whiteLEDPin, LOW);  
          Serial.println("White LED Turned OFF (from string)");
        } else {
          Serial.print("Unknown string value for White LED state: "); Serial.println(whiteLEDStateStr);
        }
      } else {
        Serial.print("Unexpected data type for White LED state: ");
        Serial.println(fbWhiteLEDData.dataType());
        Serial.print("Payload: "); Serial.println(fbWhiteLEDData.payload());
      }
    }
  }

  // implement non-blocking for the LED toggle
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorRead >= SENSOR_INTERVAL) {
    lastSensorRead = currentMillis;

    Serial.println("-------------------------------------");
    Serial.println("Reading sensors and sending to Firebase...");

    // clear OLED and set cursor
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(WHITE);
    display.setCursor(0, 0);

    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    duration = pulseIn(echoPin, HIGH);
    distance = (duration / 2) / 29.1;

    // read temperature and humidity from the DHT11 module
    float humidity = dht.readHumidity();
    float temperature = dht.readTemperature();

    // read LDR values
    int ldrValue = analogRead(ldrPin);

    if (isnan(humidity) || isnan(temperature)) {
      Serial.println("Failed to read from DHT sensor!");
      display.print("DHT11 module error");
    } else {

      // prepare payload strings
      String tempPayload = "Temp:" + String(temperature, 1) + "°C";
      String humidityPayload = "Humidity:" + String(humidity, 1) + "%";
      String ldrPayload = "LDR:" + String(ldrValue) + "Ω";
      String distancePayload = "Distance:" + String(distance) + "cm";

      // encrypt the sensor data
      String tempEncrypted = encryptSensorData(tempPayload);
      String humidityEncrypted = encryptSensorData(humidityPayload);
      String ldrEncrypted = encryptSensorData(ldrPayload);
      String distanceEncrypted = encryptSensorData(distancePayload);
      
      // compute the hash for the sensor data
      String tempHash = hashSensorData(tempPayload);
      String humidityHash = hashSensorData(humidityPayload);
      String ldrHash = hashSensorData(ldrPayload);
      String distanceHash = hashSensorData(distancePayload);

      // log to Firebase 
      sendSensorToFirebase("temperature", tempEncrypted, tempHash);
      sendSensorToFirebase("humidity", humidityEncrypted, humidityHash);
      sendSensorToFirebase("ldr", ldrEncrypted, ldrHash);
      sendSensorToFirebase("distance", distanceEncrypted, distanceHash);

      // display on OLED
      String combinedPayload = tempPayload + ", " + humidityPayload + ", " + ldrPayload + ", " + distancePayload;
      display.println("Plaintext:");
      display.println(combinedPayload);
      display.println("Ciphertext (Temp):");
      display.println(tempEncrypted);
      display.println("Ciphertext (Humidity):");
      display.println(humidityEncrypted);

      // serial output
      Serial.print("Payload: "); Serial.println(combinedPayload);
      Serial.print("Encrypted Temp: "); Serial.println(tempEncrypted);
      Serial.print("SHA-256 Temp: "); Serial.println(tempHash);
      Serial.print("Encrypted Hum: "); Serial.println(humidityEncrypted);
      Serial.print("SHA-256 Hum: "); Serial.println(humidityHash);
      Serial.print("Encrypted LDR: "); Serial.println(ldrEncrypted);
      Serial.print("SHA-256 LDR: "); Serial.println(ldrHash);
      Serial.print("Encrypted distance: "); Serial.println(distanceEncrypted);
      Serial.print("SHA-256 Distance: "); Serial.println(distanceHash);
    }
    display.display();
    Serial.println("Sensor data processing and Firebase send complete.");
    Serial.println("-------------------------------------");
  }
}