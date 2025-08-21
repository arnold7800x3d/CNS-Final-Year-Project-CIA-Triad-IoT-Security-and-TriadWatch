/*
  Refactored Arduino MKR GSM 1400 code
  Features:
    - ultrasonic sensor         - detect approaching objects
    - light dependent resistor  - detect light-based tampering
    - buzzer                    - alarm indicator
    - OLED screen               - display
*/

// libraries
#include <Servo.h>
#include <Adafruit_SSD1306.h> 
#include <Adafruit_GFX.h>

// OLED display configuration
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
#define SCREEN_ADDRESS 0X3C

// variables
const int servoPin = 6; // pin for the servo motor
const int buzzerPin = 1; // pin for the buzzer
const int ldrPin = A0; // pin for the LDR
const int trigPin = 7; // pin for the ultrasonic trigger
const int echoPin = 5; // pin for the ultrasonic echo
long duration, cm, inches; // ultrasonic distance variables

// servo timing variables and objects
Servo servoMotor; 
int angle = 0; 
unsigned long lastMotionTime = 0;

// buzzer state variable
bool buzzerOn = false;

// light detection variables
unsigned long lastLightDetectionTime = 0; 

// objects
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

void setup() {
  Serial.begin(9600);

  pinMode(buzzerPin, OUTPUT); 
  pinMode(ldrPin, INPUT); 
  pinMode(trigPin, OUTPUT); 
  pinMode(echoPin, INPUT); 

  // OLED initialization
  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
    for (;;);
  }

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Initializing display");
  display.display();
  delay(2000);
  
  // attach the servo to the pin number
  servoMotor.attach(servoPin);
}

void loop() {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(WHITE);
  display.setCursor(0, 0);
  
  detectObject(); 
  detectLight(); 

  display.display();
  delay(1000);
}

// ultrasonic scanning
void detectObject() {
  unsigned long currentTime = millis();

  if (currentTime - lastMotionTime >= 500) {
    lastMotionTime = currentTime;

    if (angle <= 120) {
      servoMotor.write(angle);
      measureDistance(angle);
      angle += 10;
    } else { 
      angle = 0; 
    }
  }
}

// measure distance
void measureDistance(int angle) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(5);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  duration = pulseIn(echoPin, HIGH);
  cm = (duration / 2) / 29.1;
  inches = (duration / 2) / 74;

  Serial.print("Angle: "); Serial.println(angle);
  Serial.print("Distance: "); Serial.print(cm); Serial.println(" cm");

  display.print("Angle: "); display.println(angle);
  display.print("Distance: "); display.print(cm); display.println("cm");

  if (cm <= 10) {
    soundBuzzer();
  } else {
    turnBuzzerOff();
  }
}

// buzzer functions
void soundBuzzer() {
  if (!buzzerOn) {
    tone(buzzerPin, 2000);
    buzzerOn = true;
  }
}

void turnBuzzerOff() {
  if (buzzerOn) {
    noTone(buzzerPin);
    buzzerOn = false;
  }
}

// detect light
void detectLight() {
  unsigned long currentTime = millis();
  
  if (currentTime - lastLightDetectionTime >= 300) {
    lastLightDetectionTime = currentTime;

    int ldrValue = analogRead(ldrPin); 
    Serial.print("LDR value: "); Serial.println(ldrValue); 
    display.print("LDR value: "); display.println(ldrValue); 

    if (ldrValue <= 400) {
      tone(buzzerPin, 2000);
    } else {
      if (!buzzerOn) {
        noTone(buzzerPin);
      }
    }
  }
}
