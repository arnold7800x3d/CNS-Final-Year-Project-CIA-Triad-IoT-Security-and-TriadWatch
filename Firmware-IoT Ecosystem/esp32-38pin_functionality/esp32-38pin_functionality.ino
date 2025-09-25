/*
  this is the source code for the esp32 38-pin functionality of the bank monitoring system. it features:
    - esp32 38pin               - microcontroller
    - dht11 module              - temperature and humidity readings
    - OLED display              - display
    - LED                       - status indicators and real time control
    - PIR                       - detect motion
    - buzzer                    - alarm indicator
    - light dependent resistor  - detect light based tampering
    - ultrasonic sensor         - detect approaching objects
    - A9G module                - gsm functionality
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

// TLS security
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

// a9g configuration
#include <HardwareSerial.h>

// a9g tx and rx configurations
#define A9G_TX_PIN 27
#define A9G_RX_PIN 14

// dht11 variables
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
const char* caCert =
  "-----BEGIN CERTIFICATE-----\n"
  "MIIERzCCAy+gAwIBAgIUI+6cAOfTb4RCOpQIRxCFkxuwWHUwDQYJKoZIhvcNAQEL\n"
  "BQAwgbIxCzAJBgNVBAYTAktFMRAwDgYDVQQIDAdOYWlyb2JpMRAwDgYDVQQHDAdO\n"
  "YWlyb2JpMSQwIgYDVQQKDBtUcmlhZFdhdGNoIElvVCBTZWN1cml0eSBMdGQxHjAc\n"
  "BgNVBAsMFUN5YmVyc2VjdXJpdHkgYW5kIElvVDEPMA0GA1UEAwwGQXJub2xkMSgw\n"
  "JgYJKoZIhvcNAQkBFhlhcm5vbGRvY2hpZW5nOTVAZ21haWwuY29tMB4XDTI1MDky\n"
  "NDIzMzgyMVoXDTI2MDkyNDIzMzgyMVowgbIxCzAJBgNVBAYTAktFMRAwDgYDVQQI\n"
  "DAdOYWlyb2JpMRAwDgYDVQQHDAdOYWlyb2JpMSQwIgYDVQQKDBtUcmlhZFdhdGNo\n"
  "IElvVCBTZWN1cml0eSBMdGQxHjAcBgNVBAsMFUN5YmVyc2VjdXJpdHkgYW5kIElv\n"
  "VDEPMA0GA1UEAwwGQXJub2xkMSgwJgYJKoZIhvcNAQkBFhlhcm5vbGRvY2hpZW5n\n"
  "OTVAZ21haWwuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu93r\n"
  "KeFiNAeM6QiRaYa/fHfs5CE/1AZ+iNjk/9WUbuG3VZ9vg7hjHpHBKFV6IJd/NWE0\n"
  "VH1/sSR0KjCSklj1sMlXbfcf6dQkxZC5odfMoruh8jO1Vd2m4yjqlM2Jzi51+/ry\n"
  "iLyIz+uJ7BNpOtYOZAx/3yCt4MraLX4n01HQO62rgj5VxpGeCeK7T+a8WMBhQKgU\n"
  "HqiNXCg5glAytQK2M5n4aMj0V4Yolv/b9B6hp7lNnvGly8B1QEDv3XDb8ox99owT\n"
  "RYAD3RBhORr7ALIJO0kPcPpJg+hNm9hBiSpAXdukrjEU5hSE3JMP07j+rllMRXwB\n"
  "nyHMJmVsh3gdle1z/wIDAQABo1MwUTAdBgNVHQ4EFgQUqxdVSASGLy4QZO+izDz1\n"
  "8wQRI20wHwYDVR0jBBgwFoAUqxdVSASGLy4QZO+izDz18wQRI20wDwYDVR0TAQH/\n"
  "BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAIjhpC1HflTwk6CE26jnKtfhAopnP\n"
  "+TXkVWU7JjgQTgwnVJSFlRRbJXY+P+Oa/XtbJSK5j56z0uFKRnn1q85K657ALIIt\n"
  "/JX6ai9Nlsc0sHuwCG5CgFLEBaKdaQDwLeNxpzrzfCrnLIrUzRNz8xLYL8jGGprh\n"
  "8ku7q6q8kjEbQBCjMTg9R3bxN3UdA/xkAECaSzSuarbP0aXI4nOJNLHmg2NMw6mL\n"
  "1KFG+71YRo25reu29j5ny3qt235gzd0VzUy8kbwGODwJk6yZNYsPZ75Wl3/V1Zz9\n"
  "Z8otF+PArm23yGkyjCnIbc+5Ow9rnRrLruk2JhA51TZkancJ7V2x2DRjBQ==\n"
  "-----END CERTIFICATE-----\n";

// pin configurations
const int blueLEDPin = 25;
const int whiteLEDPin = 26;
const int buzzerPin = 19;
const int ldrPin = 34;
const int trigPin = 18;
const int echoPin = 5;
const int pirPin = 33;

// variables
long duration, distance;
bool motionDetected = false;
unsigned long lastMotionTime = 0;
const unsigned long motionCooldown = 10000;
unsigned long otpExpiryTime = 0;
const unsigned long OTP_VALIDITY = 300000;
bool otpCooldown = false;               // handle spamming
unsigned long otpCooldownTime = 60000;  // 60 seconds cooldown
bool otpPending = false;
String correctOtp = "";
const char* OTP_VERIFY_TOPIC = "bank_monitoring/otpVerify";
const char* OTP_RESPONSE_TOPIC = "bank_monitoring/otpResponse";

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
HardwareSerial A9GSerial(1);  // make use of UART 1

// function for connecting the mqtt broker
void connectMQTT() {
  secureClient.setCACert(caCert);

  if (!mqttClient.connected()) {  // Check again before entering while loop
    Serial.println("MQTT disconnected, attempting reconnect from connectMQTT().");
    attemptMqttConnectionAndSubscribe();  // Reuse the helper
    if (!mqttClient.connected()) {        // If still not connected after one attempt
      Serial.println("Reconnect attempt failed. Will retry in loop(). Delaying...");
      delay(5000);  // Add a delay before next attempt from loop()
    }
  }
}

void attemptMqttConnectionAndSubscribe() {
  if (!mqttClient.connected()) {  // Only attempt if not already connected
    Serial.print("Attempting MQTT connection (and subscription)...");
    String clientId = "ESP32Client-" + String(WiFi.macAddress());  // More unique client ID
    if (mqttClient.connect(clientId.c_str(), mqttUser, mqttPass)) {
      Serial.println("MQTT Connected!");
      // Subscribe to topics
      if (mqttClient.subscribe("bank_monitoring/led/white")) {  // Check subscription success
        Serial.println("Subscribed to bank_monitoring/led/white");
      } else {
        Serial.println("ERROR subscribing to bank_monitoring/led/white");
      }
      if (mqttClient.subscribe("bank_monitoring/led/blue")) {
        Serial.println("Subscribed to bank_monitoring/led/blue");
      } else {
        Serial.println("ERROR subscribing to bank_monitoring/led/blue");
      }
      if (mqttClient.subscribe("bank_monitoring/otpRequest")) {
        Serial.println("Subscribed to bank_monitoring/otpRequest");
      }
      if (mqttClient.subscribe("bank_monitoring/otpVerify")) {
        Serial.println("Subscribed to bank_monitoring/otpVerify");
      } else {
        Serial.println("ERROR subscribing to bank_monitoring/otpVerify");
      }
    } else {
      Serial.print("MQTT connect failed, rc=");
      Serial.print(mqttClient.state());
      Serial.println(" Will retry in loop()...");
    }
  }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("]: ");
  Serial.println(message);

  Serial.print("Raw payload bytes: ");
  for (int i = 0; i < length; i++) Serial.print((int)payload[i]);
  Serial.println();

  if (String(topic) == "bank_monitoring/otpRequest" && message == "true") {
    if (!otpCooldown) {
      otpCooldown = true;
      otpExpiryTime = millis() + OTP_VALIDITY;
      generateAndSendOTP();  // <- generate OTP AND send SMS immediately
    } else {
      Serial.println("OTP request ignored due to cooldown");
    }
  }

  // --- OTP Verification ---
  if (String(topic) == "bank_monitoring/otpVerify") {
    if (millis() > otpExpiryTime) {
      mqttClient.publish("bank_monitoring/otpResponse", "EXPIRED");
      Serial.println("OTP verification attempt: EXPIRED");
    } else if (message == correctOtp) {
      mqttClient.publish("bank_monitoring/otpResponse", "SUCCESS");
      Serial.println("OTP verification: SUCCESS");
      correctOtp = "";  // reset OTP after successful verification
    } else {
      mqttClient.publish("bank_monitoring/otpResponse", "FAIL");
      Serial.println("OTP verification: FAIL");
    }
  }

  // control LEDs based on topic and message
  if (String(topic) == "bank_monitoring/led/white") {
    if (message == "ON") {
      digitalWrite(whiteLEDPin, HIGH);
      Serial.println("White LED turned ON");
    } else if (message == "OFF") {
      digitalWrite(whiteLEDPin, LOW);
      Serial.println("White LED turned OFF");
    }
  }

  if (String(topic) == "bank_monitoring/led/blue") {
    if (message == "ON") {
      digitalWrite(blueLEDPin, HIGH);
      Serial.println("Blue LED turned ON");
    } else if (message == "OFF") {
      digitalWrite(blueLEDPin, LOW);
      Serial.println("Blue LED turned OFF");
    }
  }
}

// function for publishing sensor data over mqtt
void publishSensorData(String tempEnc, String tempHash,
                       String humEnc, String humHash,
                       String ldrEnc, String ldrHash,
                       String distEnc, String distHash,
                       String motionEnc, String motionHash) {
  String tempPayload = "{\"cipher\":\"" + tempEnc + "\", \"hash\":\"" + tempHash + "\"}";
  String humPayload = "{\"cipher\":\"" + humEnc + "\",  \"hash\":\"" + humHash + "\"}";
  String ldrPayload = "{\"cipher\":\"" + ldrEnc + "\",  \"hash\":\"" + ldrHash + "\"}";
  String distPayload = "{\"cipher\":\"" + distEnc + "\", \"hash\":\"" + distHash + "\"}";
  String motionPayload = "{\"cipher\":\"" + motionEnc + "\", \"hash\":\"" + motionHash + "\"}";

  // publish to corresponding topics
  mqttClient.publish("bank_monitoring/temperature", tempPayload.c_str());
  mqttClient.publish("bank_monitoring/humidity", humPayload.c_str());
  mqttClient.publish("bank_monitoring/ldr", ldrPayload.c_str());
  mqttClient.publish("bank_monitoring/distance", distPayload.c_str());
  mqttClient.publish("bank_monitoring/motion", motionPayload.c_str());
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

void printA9GResponse() {
  while (A9GSerial.available()) {
    char c = A9GSerial.read();
    Serial.write(c);
  }
}

void sendSMS(String phoneNumber, String message) {
  Serial.println("Preparing to send SMS...");

  // Clear any pending data
  while (A9GSerial.available()) {
    A9GSerial.read();
  }

  // Set SMS to text mode (same as test)
  A9GSerial.println("AT+CMGF=1");
  delay(1000);
  printA9GResponse();

  // Send recipient number (same as test)
  A9GSerial.print("AT+CMGS=\"");
  A9GSerial.print(phoneNumber);
  A9GSerial.println("\"");
  delay(1000);  // Reduced delay to match working test
  printA9GResponse();

  // Send message content (same as test)
  A9GSerial.print(message);
  delay(500);

  // Send CTRL+Z (same as test)
  A9GSerial.write(26);
  Serial.println("CTRL+Z sent");

  // Wait for response with shorter delay (like test)
  delay(5000);
  printA9GResponse();

  Serial.println("SMS send process completed.");
}


// otp generation
String generateOTP() {
  long otpNumber = esp_random() % 1000000;  // 0-999999
  String otpStr = String(otpNumber);
  while (otpStr.length() < 6) otpStr = "0" + otpStr;  // pad with zeros
  return otpStr;
}

void generateAndSendOTP() {
  correctOtp = generateOTP();
  otpExpiryTime = millis() + OTP_VALIDITY;
  otpCooldown = true;
  Serial.println("Generated OTP: " + correctOtp);
  sendSMS("+254795975000", "Your OTP is: " + correctOtp);
}

void checkA9GStatus() {
  Serial.println("=== A9G Module Diagnostics ===");

  // Basic AT test
  A9GSerial.println("AT");
  delay(2000);
  printA9GResponse();

  // Check SIM card status
  A9GSerial.println("AT+CPIN?");
  delay(2000);
  printA9GResponse();

  // Check network registration
  A9GSerial.println("AT+CREG?");
  delay(2000);
  printA9GResponse();

  // Check signal strength
  A9GSerial.println("AT+CSQ");
  delay(2000);
  printA9GResponse();

  // Check network operator
  A9GSerial.println("AT+COPS?");
  delay(2000);
  printA9GResponse();

  Serial.println("=== End Diagnostics ===");
}

void setup() {
  Serial.begin(115200);

  // pin setup
  pinMode(blueLEDPin, OUTPUT);
  pinMode(whiteLEDPin, OUTPUT);
  digitalWrite(blueLEDPin, LOW);
  digitalWrite(whiteLEDPin, LOW);
  pinMode(ldrPin, INPUT);
  pinMode(buzzerPin, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(pirPin, INPUT);

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

  // initialize a9g module
  A9GSerial.begin(115200, SERIAL_8N1, A9G_RX_PIN, A9G_TX_PIN);
  delay(5000);  // allow module to boot
  Serial.println("A9G module ready for SMS");

  checkA9GStatus();

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
  secureClient.setCACert(caCert);  // set CA before connecting
  mqttClient.setServer(mqttServer, mqttPort);

  mqttClient.setCallback(mqttCallback);
  attemptMqttConnectionAndSubscribe();

  // --- Print free heap after all init ---
  Serial.print("Free heap after setup: ");
  Serial.println(ESP.getFreeHeap());
}

void loop() {
  if (!mqttClient.connected()) {
    connectMQTT();
  }
  mqttClient.loop();

  // reset cooldown
  static unsigned long lastCooldownCheck = 0;
  if (otpCooldown && millis() - lastCooldownCheck > otpCooldownTime) {
    otpCooldown = false;
    lastCooldownCheck = millis();
    Serial.println("OTP cooldown reset, ready for next request");
  }

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

    // read distance values
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

    // read motion variable
    motionDetected = digitalRead(pirPin);
    String motionPayload;

    // Implement cooldown to avoid spamming
    if (motionDetected && (millis() - lastMotionTime > motionCooldown)) {
      motionPayload = "Detected";
      lastMotionTime = millis();
    } else if (!motionDetected) {
      motionPayload = "Not Detected";
    } else {
      // During cooldown, keep last state
      motionPayload = "Not Detected";
    }

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
      String motionEncrypted = encryptSensorData(motionPayload);

      // compute the hash for the sensor data
      String tempHash = hashSensorData(tempPayload);
      String humidityHash = hashSensorData(humidityPayload);
      String ldrHash = hashSensorData(ldrPayload);
      String distanceHash = hashSensorData(distancePayload);
      String motionHash = hashSensorData(motionPayload);

      unsigned long afterEncryptMillis = millis();
      Serial.print("After encryption (ms): ");
      Serial.println(afterEncryptMillis);

      publishSensorData(tempEncrypted, tempHash,
                        humidityEncrypted, humidityHash,
                        ldrEncrypted, ldrHash,
                        distanceEncrypted, distanceHash,
                        motionEncrypted, motionHash);

      unsigned long afterMQTTMillis = millis();
      Serial.print("After MQTT publish (ms): ");
      Serial.println(afterMQTTMillis);

      // display on OLED
      String combinedPayload = tempPayload + ", " + humidityPayload + ", " + ldrPayload + ", " + distancePayload;
      display.println("Temp: " + String(temperature, 1) + "C");
      display.println("Humidity: " + String(humidity, 1) + "%");
      display.println("LDR: " + String(ldrValue));
      display.println("Distance: " + String(distance) + "cm");
      display.println("Motion: " + motionPayload);
      display.display();

      // serial output
      //Serial.println("Plaintext: " + combinedPayload);
      //Serial.println("Temp: " + tempPayload + " | Encrypted: " + tempEncrypted + " | Hash: " + tempHash);
      //Serial.println("Humidity: " + humidityPayload + " | Encrypted: " + humidityEncrypted + " | Hash: " + humidityHash);
      //Serial.println("LDR: " + ldrPayload + " | Encrypted: " + ldrEncrypted + " | Hash: " + ldrHash);
      //Serial.println("Distance: " + distancePayload + " | Encrypted: " + distanceEncrypted + " | Hash: " + distanceHash);
      //Serial.println("Motion: " + motionPayload + " | Encrypted: " + motionEncrypted + " | Hash: " + motionHash);
    }
    display.display();
    Serial.println("Sensor data processing and MQTT send complete.");
    Serial.println("-------------------------------------");
  }
}