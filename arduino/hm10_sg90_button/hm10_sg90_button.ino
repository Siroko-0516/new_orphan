// SPDX-License-Identifier: AGPL-3.0-only
// UNO R3 + HM-10 UART + SG90 + momentary button. Read README before wiring.
#include <Arduino.h>
#include <Servo.h>
#include <string.h>

const uint8_t SERVO_PIN = 2;
const uint8_t BUTTON_PIN = 3;
// true: powered 3-pin button module (S/V/G, with its own resistor).
// false: bare 2-pin switch between D3 and GND.
const bool BUTTON_MODULE = true;
const uint8_t CENTER_ANGLE = 90;
const uint8_t LEFT_ANGLE = 80;
const uint8_t RIGHT_ANGLE = 100;
// Hardware UART shared with USB. Disconnect D0/D1 wires before uploading.
// HM-10 TX -> UNO D0 (RX); UNO D1 (TX) -> divider -> HM-10 RX.
HardwareSerial &hm10 = Serial;
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
uint8_t releasedLevel = HIGH;
unsigned long buttonChangedAt = 0;
bool assistantPending = false;
unsigned long lastAssistAt = 0;

void startWarning() {
  if (moving) return; // Coalesce alerts; no queued/unbounded motor movement.
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
  }
}

void setup() {
  pinMode(BUTTON_PIN, BUTTON_MODULE ? INPUT : INPUT_PULLUP);
  pinMode(SERVO_PIN, OUTPUT);
  digitalWrite(SERVO_PIN, LOW); // No unsolicited movement at boot.
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
  hm10.begin(9600);
  // Release the button while powering up/resetting. This accepts either
  // active-HIGH or active-LOW modules, and avoids a false press at startup.
  delay(200);
  releasedLevel = digitalRead(BUTTON_PIN);
  rawButton = stableButton = releasedLevel;
  buttonChangedAt = millis();
  lastAssistAt = millis() - 1000UL;
  // This UART carries the BLE protocol. Do not print debug messages here.
}

void loop() {
  bool sample = digitalRead(BUTTON_PIN);
  if (sample != rawButton) { rawButton = sample; buttonChangedAt = millis(); }
  if (sample != stableButton && millis() - buttonChangedAt >= 40UL) {
    stableButton = sample;
    if (stableButton != releasedLevel) assistantPending = true;
  }
  // Drain UART even during a gesture; startWarning() coalesces extra alerts.
  while (hm10.available()) {
    char c = static_cast<char>(hm10.read());
    if (c == '\r') continue;
    if (c == '\n') {
      if (!discardLine) {
        line[used] = '\0';
        if (strcmp(line, "TEST") == 0 || strcmp(line, "OBSTACLE") == 0) {
          startWarning();
        }
      }
      used = 0; discardLine = false;
    } else if (!discardLine) {
      if (used < sizeof(line) - 1) line[used++] = c;
      else { used = 0; discardLine = true; }
    }
  }
  updateWarning();
  if (!moving && assistantPending && millis() - lastAssistAt >= 800UL) {
    hm10.print("ASSIST\n");
    assistantPending = false;
    lastAssistAt = millis();
  }
}
