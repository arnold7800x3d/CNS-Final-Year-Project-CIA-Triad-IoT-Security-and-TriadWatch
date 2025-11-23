// import necessary libraries
#include "DHT.h"  // DHT11 library
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <mbedtls/aes.h>       // AES-256 encryption library
#include "mbedtls/sha256.h"    // SHA-256 encryption
#include "arduino_base64.hpp"  // base64 encoding library
#include "secrets.h"           // file containing WiFi credentials
//#include <BluetoothSerial.h>
#include <SPIFFS.h>
#include "FS.h"

// wi-fi transmission
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

// mqtt server settings
const char* mqttServer = SECRET_MQTT_SERVER;
const int mqttPort = 8883;
const char* mqttUser = SECRET_MQTT_USER;
const char* mqttPass = SECRET_MQTT_PASSWORD;

// root ca certificate (on debian 11 vm)
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
unsigned long wifiTimeout = 10000;  // 10 seconds
unsigned long mqttTimeout = 10000;  // 10 seconds

// objects
DHT dht(DHTPIN, DHTTYPE);
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);
HardwareSerial A9GSerial(1);  // make use of UART 1
//BluetoothSerial SerialBT;

// function for connecting the mqtt broker
void connectMQTT() {
  secureClient.setCACert(caCert);

  if (!mqttClient.connected()) {  // check again before entering while loop
    Serial.println(F("MQTT disconnected, attempting reconnect from connectMQTT()."));
    attemptMqttConnectionAndSubscribe();  // reuse the helper
    if (!mqttClient.connected()) {        // if still not connected after one attempt
      Serial.println(F("Reconnect attempt failed. Will retry in loop(). Delaying..."));
      delay(5000);  // add a delay before next attempt from loop()
    }
  }
}

void attemptMqttConnectionAndSubscribe() {
  if (!mqttClient.connected()) {  // only attempt if not already connected
    Serial.print(F("Attempting MQTT connection (and subscription)..."));
    String clientId = "ESP32Client-" + String(WiFi.macAddress());  // more unique client ID
    if (mqttClient.connect(clientId.c_str(), mqttUser, mqttPass)) {
      Serial.println(F("MQTT Connected!"));
      // subscribe to topics
      if (mqttClient.subscribe("bank_monitoring/led/white")) {  // check subscription success
        Serial.println(F("Subscribed to bank_monitoring/led/white"));
      } else {
        Serial.println(F("ERROR subscribing to bank_monitoring/led/white"));
      }
      if (mqttClient.subscribe("bank_monitoring/led/blue")) {
        Serial.println(F("Subscribed to bank_monitoring/led/blue"));
      } else {
        Serial.println(F("ERROR subscribing to bank_monitoring/led/blue"));
      }
      if (mqttClient.subscribe("bank_monitoring/otpRequest")) {
        Serial.println(F("Subscribed to bank_monitoring/otpRequest"));
      }
      if (mqttClient.subscribe("bank_monitoring/otpVerify")) {
        Serial.println(F("Subscribed to bank_monitoring/otpVerify"));
      } else {
        Serial.println(F("ERROR subscribing to bank_monitoring/otpVerify"));
      }
    } else {
      Serial.print(F("MQTT connect failed, rc="));
      Serial.print(mqttClient.state());
      Serial.println(F(" Will retry in loop()..."));
    }
  }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print(F("Message arrived ["));
  Serial.print(topic);
  Serial.print(F("]: "));
  Serial.println(message);

  Serial.print(F("Raw payload bytes: "));
  for (int i = 0; i < length; i++) Serial.print((int)payload[i]);
  Serial.println();

  if (String(topic) == "bank_monitoring/otpRequest" && message == "true") {
    if (!otpCooldown) {
      otpCooldown = true;
      otpExpiryTime = millis() + OTP_VALIDITY;
      generateAndSendOTP();  // generate OTP AND send SMS immediately
    } else {
      Serial.println(F("OTP request ignored due to cooldown"));
    }
  }

  // otp verification
  if (String(topic) == "bank_monitoring/otpVerify") {
    if (millis() > otpExpiryTime) {
      mqttClient.publish("bank_monitoring/otpResponse", "EXPIRED");
      Serial.println("OTP verification attempt: EXPIRED");
    } else if (message == correctOtp) {
      mqttClient.publish("bank_monitoring/otpResponse", "SUCCESS");
      Serial.println(F("OTP verification: SUCCESS"));
      correctOtp = "";  // reset OTP after successful verification
    } else {
      mqttClient.publish("bank_monitoring/otpResponse", "FAIL");
      Serial.println(F("OTP verification: FAIL"));
    }
  }

  // control LEDs based on topic and message
  if (String(topic) == "bank_monitoring/led/white") {
    if (message == "ON") {
      digitalWrite(whiteLEDPin, HIGH);
      Serial.println(F("White LED turned ON"));
    } else if (message == "OFF") {
      digitalWrite(whiteLEDPin, LOW);
      Serial.println(F("White LED turned OFF"));
    }
  }

  if (String(topic) == "bank_monitoring/led/blue") {
    if (message == "ON") {
      digitalWrite(blueLEDPin, HIGH);
      Serial.println(F("Blue LED turned ON"));
    } else if (message == "OFF") {
      digitalWrite(blueLEDPin, LOW);
      Serial.println(F("Blue LED turned OFF"));
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

String generateSensorJSON(String tempEnc, String tempHash,
                          String humEnc, String humHash,
                          String ldrEnc, String ldrHash,
                          String distEnc, String distHash,
                          String motionEnc, String motionHash) {
  String jsonPayload = "{";
  jsonPayload += "\"temperature\":{\"cipher\":\"" + tempEnc + "\",\"hash\":\"" + tempHash + "\"},";
  jsonPayload += "\"humidity\":{\"cipher\":\"" + humEnc + "\",\"hash\":\"" + humHash + "\"},";
  jsonPayload += "\"ldr\":{\"cipher\":\"" + ldrEnc + "\",\"hash\":\"" + ldrHash + "\"},";
  jsonPayload += "\"distance\":{\"cipher\":\"" + distEnc + "\",\"hash\":\"" + distHash + "\"},";
  jsonPayload += "\"motion\":{\"cipher\":\"" + motionEnc + "\",\"hash\":\"" + motionHash + "\"}";
  jsonPayload += "}";
  return jsonPayload;
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
  Serial.println(F("Preparing to send SMS..."));

  // clear any pending data
  while (A9GSerial.available()) {
    A9GSerial.read();
  }

  // set sms to text mode
  A9GSerial.println("AT+CMGF=1");
  delay(1000);
  printA9GResponse();

  // send recipient number
  A9GSerial.print("AT+CMGS=\"");
  A9GSerial.print(phoneNumber);
  A9GSerial.println("\"");
  delay(1000);
  printA9GResponse();

  // send message content
  A9GSerial.print(message);
  delay(500);

  // send CTRL+Z
  A9GSerial.write(26);
  Serial.println(F("CTRL+Z sent"));

  // wait for response with shorter delay
  delay(5000);
  printA9GResponse();

  Serial.println(F("SMS send process completed."));
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

void connectWifi() {
  Serial.println(F("Connecting to Wi-Fi..."));
  WiFi.begin(SECRET_SSID, SECRET_PASSWORD);
  unsigned long startAttempt = millis();

  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < wifiTimeout) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(F("\nWiFi connected!"));
  } else {
    Serial.println(F("\nWiFi connection failed — proceeding without it."));
  }
}

// Log unsent data to SPIFFS
void handleUnsentData(String payload, bool viewFile = false) {
  // Ensure SPIFFS is mounted
  if (!SPIFFS.begin(true)) {
    Serial.println("❌ Failed to mount SPIFFS");
    return;
  }

  // Step 1: Check file size before writing
  if (SPIFFS.exists("/unsent.txt")) {
    File file = SPIFFS.open("/unsent.txt", FILE_READ);
    if (file) {
      size_t size = file.size();
      file.close();

      // Step 2: If file too large (> 1 MB), delete it
      if (size > 1024 * 1024) {
        SPIFFS.remove("/unsent.txt");
        Serial.println("⚠️ unsent.txt exceeded 1MB, deleted to free space");
      }
    }
  }

  // Step 3: Append new payload
  File file = SPIFFS.open("/unsent.txt", FILE_APPEND);
  if (!file) {
    Serial.println("❌ Failed to open unsent.txt for writing");
    return;
  }

  file.println(payload);
  file.close();
  Serial.println("✅ Data saved to unsent.txt");

  // Step 4 (optional): View file contents
  if (viewFile) {
    File view = SPIFFS.open("/unsent.txt", FILE_READ);
    if (!view) {
      Serial.println("❌ Failed to open unsent.txt for reading");
      return;
    }

    Serial.println("📂 Contents of unsent.txt:");
    while (view.available()) {
      Serial.write(view.read());
    }
    view.close();
    Serial.println("\n📄 End of file");
  }
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
  connectWifi();

    // Initialize SPIFFS
  if (!SPIFFS.begin(true)) {
    Serial.println("SPIFFS Mount Failed!");
    return;
  }

  // print free heap for debugging
  Serial.print(F("Free heap after Wi-Fi connect: "));
  Serial.println(ESP.getFreeHeap());

  // initialize a9g module
  A9GSerial.begin(115200, SERIAL_8N1, A9G_RX_PIN, A9G_TX_PIN);
  delay(5000);  // allow module to boot
  Serial.println(F("A9G module ready for SMS"));

  //SerialBT.begin("ESP32_SensorBackup");  // Bluetooth device name
  //Serial.println(F("Bluetooth started, waiting for laptop to pair..."));

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

  // initialize mqtt
  Serial.println(F("Setting up MQTT client..."));
  secureClient.setCACert(caCert);  // set ca cert before connecting
  mqttClient.setServer(mqttServer, mqttPort);

  mqttClient.setCallback(mqttCallback);
  attemptMqttConnectionAndSubscribe();

  // print free heap after all init
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
    Serial.println(F("OTP cooldown reset, ready for next request"));
  }

  // implement non-blocking for the LED toggle
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorRead >= SENSOR_INTERVAL) {
    lastSensorRead = currentMillis;

    Serial.println(F("-------------------------------------"));
    Serial.println(F("Reading sensors and sending to MQTT broker. Timestamp (ms):"));
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

    // cooldown to avoid spamming
    if (motionDetected && (millis() - lastMotionTime > motionCooldown)) {
      motionPayload = "Detected";
      lastMotionTime = millis();
    } else if (!motionDetected) {
      motionPayload = "Not Detected";
    } else {
      // during cooldown, keep last state
      motionPayload = "Not Detected";
    }

    unsigned long afterReadMillis = millis();
    Serial.print(F("After reading sensors (ms): "));
    Serial.println(afterReadMillis);

    if (isnan(humidity) || isnan(temperature)) {
      Serial.println(F("Failed to read from DHT sensor!"));
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
      Serial.print(F("After encryption and hashing (ms): "));
      Serial.println(afterEncryptMillis);

      String jsonPayload = generateSensorJSON(tempEncrypted, tempHash, humidityEncrypted, humidityHash, ldrEncrypted, ldrHash, distanceEncrypted, distanceHash, motionEncrypted, motionHash);

      Serial.println("Temperature (DHT11): " + tempPayload + " | Encrypted: " + tempEncrypted + " | Temp Hash: " + tempHash);
      Serial.println("Humidity (DHT11): " + humidityPayload + " | Encrypted: " + humidityEncrypted + " | Humidity Hash: " + humidityHash);
      Serial.println("Distance (Ultasonic): " + distancePayload + " | Encrypted: " + distanceEncrypted + " | Distance Hash: " + distanceHash);
      Serial.println("Resistance (LDR): " + ldrPayload + " | Encrypted: " + ldrEncrypted + " | Resistance Hash: " + ldrHash);
      Serial.println("Motion (PIR): " + motionPayload + " | Encrypted: " + motionEncrypted + " | Motion Hash: " + motionHash);

      if(WiFi.status() == WL_CONNECTED){
        publishSensorData(tempEncrypted, tempHash,
                          humidityEncrypted, humidityHash,
                          ldrEncrypted, ldrHash,
                          distanceEncrypted, distanceHash,
                          motionEncrypted, motionHash);

        unsigned long afterMQTTMillis = millis();
        Serial.print(F("After MQTT publish (ms): "));
        Serial.println(afterMQTTMillis);
      } else {
        //SerialBT.println(jsonPayload);
        handleUnsentData(jsonPayload, true);
      }

      // display on OLED
      display.println("Temp: " + String(temperature, 1) + "C");
      display.println("Humidity: " + String(humidity, 1) + "%");
      display.println("LDR: " + String(ldrValue));
      display.println("Distance: " + String(distance) + "cm");
      display.println("Motion: " + motionPayload);
      display.display();

    }
    display.display();
    Serial.println(F("Sensor data processing and MQTT send complete."));
    Serial.println(F("-------------------------------------"));
  }
}