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
#include "secrets.h"           // file containing WiFi credentials

// WiFi transmission and Firebase
#include <WiFi.h>
#include "time.h"
#include <Firebase_ESP_Client.h>

// TLS security
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

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

// mqtt server settings
const char* mqttServer = SECRET_MQTT_SERVER;
const int mqttPort = 8883;
const char* mqttUser = SECRET_MQTT_USER;
const char* mqttPass = SECRET_MQTT_PASSWORD;
// const char* mqttTopic = SECRET_MQTT_TOPIC;

// root ca certificate (on debian 13 vm)
const char* caCert = \
"-----BEGIN CERTIFICATE-----\n" \
"MIID5zCCAs+gAwIBAgIULAON6o/J1GsSV2iYk1qk27bevgswDQYJKoZIhvcNAQEL\n" \
"BQAwgYIxCzAJBgNVBAYTAktFMRAwDgYDVQQIDAdOYWlyb2JpMRAwDgYDVQQHDAdO\n" \
"YWlyb2JpMR8wHQYDVQQKDBZUcmlhZFdhdGNoIElvVCBTZWMgTHRkMRYwFAYDVQQL\n" \
"DA1DeWJlclNlY3VyaXR5MRYwFAYDVQQDDA1UcmlhZFdhdGNoLUNBMB4XDTI1MDgy\n" \
"OTIwMjkxOVoXDTI2MDgyOTIwMjkxOVowgYIxCzAJBgNVBAYTAktFMRAwDgYDVQQI\n" \
"DAdOYWlyb2JpMRAwDgYDVQQHDAdOYWlyb2JpMR8wHQYDVQQKDBZUcmlhZFdhdGNo\n" \
"IElvVCBTZWMgTHRkMRYwFAYDVQQLDA1DeWJlclNlY3VyaXR5MRYwFAYDVQQDDA1U\n" \
"cmlhZFdhdGNoLUNBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo5vS\n" \
"tQcxJFwOeOfyvCTYyiXMRqPsFW5sPATI5jmQV6689gamS1820DZQJ9Tcv39C6SSC\n" \
"JAHIYrWMQsKnEzLyrIodbpHq9ZDPGS4l4gzglW9zO7R/cthes7IYuS1p1AvkvmOm\n" \
"4qFJOyxbLmS1R8rB1+p/EG6aAk4VFl7LaZN0dgNJhxqrFF+LCU2BaHCAjtqvglJY\n" \
"pYvXHMTDk/wXK8swJ+zBdV6a3acb8Mb//XBmDg1REYndGsEKHr2nltP5q71PdZDo\n" \
"8+Oh0st6lbnTfPyDZJE2/JzZX6fqGP36tC7f16De3dcbL9+kKIhZUlt1rrsU1BkG\n" \
"CKMujTzWHF1JmWUVqQIDAQABo1MwUTAdBgNVHQ4EFgQU9ZNF2XeMXoJnkL4bTAKl\n" \
"HLg6kRgwHwYDVR0jBBgwFoAU9ZNF2XeMXoJnkL4bTAKlHLg6kRgwDwYDVR0TAQH/\n" \
"BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAayJBX1dOqIUGu9VipImseKmaLH8j\n" \
"3bQ3s479UXGN4XP+UhcDP1joYRGHye0XwIqMnoZ6MUhsYPCKNBFVrZMfrT+RAxOE\n" \
"aM9GdRnNI+/wwqjFxkY1UH6rRGmjLIq5aTRuziVApAAE30yGC2trR3AKxh1zJLyq\n" \
"WXx9ZqfPe+p/hjVsAE8PU8tI17qwfELTXO7CLZ410cwPgm1efER+1pH7Qshcpj7k\n" \
"n2YyZJvxYmNiEo4GjkTu/iTfA65QVIlyAVOxA158ZSm0qARCysrUzsNiw8BJnVmW\n" \
"6ll0xvR2BVxWkrQBWAOpcsZH475Uk53YacmcgZ83myDeeYIZ2rePxhOpGg==\n" \
"-----END CERTIFICATE-----\n";

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
WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);

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

// function for connecting the mqtt broker
void connectMQTT() {
  secureClient.setCACert(caCert);

  while (!mqttClient.connected()) {
    Serial.print("Connecting to MQTT...");
    if (mqttClient.connect("ESP32Client", mqttUser, mqttPass)) {
      Serial.println("MQTT Connected");
      mqttClient.subscribe("secure_monitoring/led/white");
      mqttClient.subscribe("secure_monitoring/led/blue");
    } else {
      Serial.print("Failed: ");
      Serial.println(mqttClient.state());
      Serial.println("Retrying in 5 seconds...");
      delay(5000);
    }
  }
}

// function for publishing sensor data over mqtt
void publishSensorData(String tempEnc, String tempHash,
                       String humEnc, String humHash,
                       String ldrEnc, String ldrHash,
                       String distEnc, String distHash) {
  String tempPayload = "{\"cipher\":\"" + tempEnc + "\", \"hash\":\"" + tempHash + "\"}";
  String humPayload = "{\"cipher\":\"" + humEnc + "\",  \"hash\":\"" + humHash + "\"}";
  String ldrPayload = "{\"cipher\":\"" + ldrEnc + "\",  \"hash\":\"" + ldrHash + "\"}";
  String distPayload = "{\"cipher\":\"" + distEnc + "\", \"hash\":\"" + distHash + "\"}";

  // publish to corresponding topics
  mqttClient.publish("secure_monitoring/temperature", tempPayload.c_str());
  mqttClient.publish("secure_monitoring/humidity", humPayload.c_str());
  mqttClient.publish("secure_monitoring/ldr", ldrPayload.c_str());
  mqttClient.publish("secure_monitoring/distance", distPayload.c_str());
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

  // --- Print free heap for debugging ---
  Serial.print("Free heap after Wi-Fi connect: ");
  Serial.println(ESP.getFreeHeap());

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

  // --- Initialize MQTT ---
  Serial.println("Setting up MQTT client...");
  secureClient.setCACert(caCert);           // set CA before connecting
  mqttClient.setServer(mqttServer, mqttPort);

  // Optional: Generate a unique client ID to avoid collisions
  String clientId = "ESP32Client-" + String(esp_random());
  if (mqttClient.connect(clientId.c_str(), mqttUser, mqttPass)) {
    Serial.println("MQTT Connected!");
  } else {
    Serial.print("MQTT connect failed: ");
    Serial.println(mqttClient.state());
    Serial.println("Will retry in loop()...");
  }

  // --- Print free heap after all init ---
  Serial.print("Free heap after setup: ");
  Serial.println(ESP.getFreeHeap());
}

void loop() {
  if (!mqttClient.connected()) {
    connectMQTT();
  }
  mqttClient.loop();

  // implement non-blocking for the LED toggle
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorRead >= SENSOR_INTERVAL) {
    lastSensorRead = currentMillis;

    Serial.println("-------------------------------------");
    Serial.println("Reading sensors and sending to MQTT broker. Timestamp (ms):");
    Serial.println(currentMillis);

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

    unsigned long afterReadMillis = millis();
    Serial.print("After reading sensors (ms): ");
    Serial.println(afterReadMillis);

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

      unsigned long afterEncryptMillis = millis();
      Serial.print("After encryption (ms): ");
      Serial.println(afterEncryptMillis);

      publishSensorData(tempEncrypted, tempHash, 
                  humidityEncrypted, humidityHash, 
                  ldrEncrypted, ldrHash, 
                  distanceEncrypted, distanceHash);

      unsigned long afterMQTTMillis = millis();
      Serial.print("After MQTT publish (ms): ");
      Serial.println(afterMQTTMillis);

      // display on OLED
      String combinedPayload = tempPayload + ", " + humidityPayload + ", " + ldrPayload + ", " + distancePayload;
      display.println("Plaintext:");
      display.println(combinedPayload);
      display.println("Ciphertext (Temp):");
      display.println(tempEncrypted);
      display.println("Ciphertext (Humidity):");
      display.println(humidityEncrypted);

      // serial output
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
      Serial.print("Encrypted LDR: ");
      Serial.println(ldrEncrypted);
      Serial.print("SHA-256 LDR: ");
      Serial.println(ldrHash);
      Serial.print("Encrypted distance: ");
      Serial.println(distanceEncrypted);
      Serial.print("SHA-256 Distance: ");
      Serial.println(distanceHash);
    }
    display.display();
    Serial.println("Sensor data processing and MQTT send complete.");
    Serial.println("-------------------------------------");
  }
}