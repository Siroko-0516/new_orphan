// SPDX-License-Identifier: AGPL-3.0-only
// UNO R3 + HM-10 UART + SG90 + momentary button. Read README before wiring.
#include <SoftwareSerial.h>
#include <Servo.h>
#include <string.h>

const uint8_t SERVO_PIN = 5;
const uint8_t BUTTON_PIN = 2;
const uint8_t CENTER_ANGLE = 90;
const uint8_t LEFT_ANGLE = 80;
const uint8_t RIGHT_ANGLE = 100;
SoftwareSerial hm10(10, 11); // UNO RX, TX; level-shift D11 to the HM-10 RX.
Servo warningServo;
char line[32];
uint8_t used = 0;
bool discardLine = false;
bool moving = false;
uint8_t phase = 0;
unsigned long phaseAt = 0;
const unsigned long phaseMs[] = {180, 160, 160, 260};
bool rawButton = HIGH;
bool stableButton = HIGH;
unsigned long buttonChangedAt = 0;
bool assistantPending = false;
unsigned long lastAssistAt = 0;

void startWarning() {
  if (moving) return; // Coalesce alerts; no queued/unbounded motor movement.
  // AVR SoftwareSerial blocks interrupts for each byte. Suspend reception only
  // during this 760ms gesture to avoid stretching the Servo library's pulses.
  hm10.stopListening();
  warningServo.attach(SERVO_PIN);
  warningServo.write(CENTER_ANGLE);
  digitalWrite(LED_BUILTIN, HIGH);
  moving = true;
  phase = 0;
  phaseAt = millis();
}

void updateWarning() {
  if (!moving || millis() - phaseAt < phaseMs[phase]) return;
  phaseAt = millis();
  phase++;
  if (phase == 1) warningServo.write(LEFT_ANGLE);
  else if (phase == 2) warningServo.write(RIGHT_ANGLE);
  else if (phase == 3) warningServo.write(CENTER_ANGLE);
  else {
    warningServo.detach();
    digitalWrite(SERVO_PIN, LOW);
    digitalWrite(LED_BUILTIN, LOW);
    moving = false;
    used = 0;
    discardLine = false;
    hm10.listen();
  }
}

void setup() {
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(SERVO_PIN, OUTPUT);
  digitalWrite(SERVO_PIN, LOW); // No unsolicited movement at boot.
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
  Serial.begin(115200);
  hm10.begin(9600);
  lastAssistAt = millis() - 1000UL;
  Serial.println("Giljabi HM-10 / SG90 / ASSIST ready");
}

void loop() {
  bool sample = digitalRead(BUTTON_PIN);
  if (sample != rawButton) { rawButton = sample; buttonChangedAt = millis(); }
  if (sample != stableButton && millis() - buttonChangedAt >= 40UL) {
    stableButton = sample;
    if (stableButton == LOW) assistantPending = true;
  }
  updateWarning();
  if (!moving && assistantPending && millis() - lastAssistAt >= 800UL) {
    hm10.print("ASSIST\n");
    Serial.println("Sent: ASSIST");
    assistantPending = false;
    lastAssistAt = millis();
  }
  while (!moving && hm10.available()) {
    char c = static_cast<char>(hm10.read());
    if (c == '\r') continue;
    if (c == '\n') {
      if (!discardLine) {
        line[used] = '\0';
        if (strcmp(line, "TEST") == 0 || strcmp(line, "OBSTACLE") == 0) {
          Serial.print("Received: "); Serial.println(line);
          startWarning();
        }
      }
      used = 0; discardLine = false;
    } else if (!discardLine) {
      if (used < sizeof(line) - 1) line[used++] = c;
      else { used = 0; discardLine = true; }
    }
  }
}
