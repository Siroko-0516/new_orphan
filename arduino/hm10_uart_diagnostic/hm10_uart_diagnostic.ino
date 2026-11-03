// SPDX-License-Identifier: AGPL-3.0-only
// Temporary, receive-only HM-10 diagnosis. Read README before connecting.
// HM-10 TXD -> UNO D0; HM-10 RXD MUST remain disconnected while logging.
// Serial TX is for the USB monitor, not for the HM-10 in this sketch.
#include <Arduino.h>
#include <Servo.h>
#include <string.h>

const unsigned long UART_BAUD = 9600;
const uint8_t SERVO_PIN = 2, BUTTON_PIN = 3;
const bool BUTTON_MODULE = true;
Servo warningServo;
const uint8_t warningAngles[] = {90,45,135,45,135,45,135,90};
const unsigned long phaseMs[] = {180,220,220,220,220,220,220,240};
const uint8_t PHASE_COUNT = sizeof(warningAngles) / sizeof(warningAngles[0]);
bool moving = false;
uint8_t phase = 0;
unsigned long phaseAt = 0;

char command[32];
uint8_t used = 0;
bool discardLine = false;
uint8_t trace[32];
uint8_t traceUsed = 0;
bool traceClipped = false, everReceived = false;
unsigned long lastRxAt = 0, lastWaitingAt = 0;
uint8_t releasedLevel = HIGH, rawButton = HIGH, stableButton = HIGH;
unsigned long buttonChangedAt = 0;

void startWarning() {
  if (moving) return; // Do not queue or restart a gesture.
  warningServo.attach(SERVO_PIN);
  warningServo.write(warningAngles[0]);
  digitalWrite(LED_BUILTIN, HIGH);
  moving = true;
  phase = 0;
  phaseAt = millis();
}

void updateWarning() {
  if (!moving || millis() - phaseAt < phaseMs[phase]) return;
  phaseAt = millis();
  if (++phase < PHASE_COUNT) warningServo.write(warningAngles[phase]);
  else {
    warningServo.detach();
    digitalWrite(SERVO_PIN, LOW);
    digitalWrite(LED_BUILTIN, LOW);
    moving = false;
    Serial.println(F("SERVO: command sequence finished"));
  }
}

void dumpTrace() {
  if (!traceUsed && !traceClipped) return;
  const char digits[] = "0123456789ABCDEF";
  Serial.print(F("RX HEX:"));
  for (uint8_t i = 0; i < traceUsed; ++i) {
    Serial.print(' ');
    Serial.print(digits[trace[i] >> 4]);
    Serial.print(digits[trace[i] & 15]);
  }
  Serial.println(traceClipped ? F(" ... (trace limited to 32 bytes)") : F(""));
  traceUsed = 0;
  traceClipped = false;
}

void receiveByte(uint8_t value) {
  everReceived = true;
  lastRxAt = millis();
  if (traceUsed < sizeof(trace)) trace[traceUsed++] = value;
  else traceClipped = true;
  if (value == '\r') return;
  if (value == '\n') {
    dumpTrace();
    command[used] = '\0';
    if (discardLine) Serial.println(F("COMMAND: rejected (non-ASCII or line too long)"));
    else if (strcmp(command, "TEST") == 0 || strcmp(command, "OBSTACLE") == 0) {
      if (moving) Serial.println(F("COMMAND: valid; servo already moving (not queued)"));
      else {
        Serial.println(F("COMMAND: valid TEST/OBSTACLE -> servo"));
        startWarning();
      }
    } else Serial.println(F("COMMAND: unrecognized; no movement"));
    used = 0;
    discardLine = false;
  } else if (!discardLine) {
    if (value < 32 || value > 126 || used >= sizeof(command) - 1) discardLine = true;
    else command[used++] = static_cast<char>(value);
  }
}

void setup() {
  pinMode(BUTTON_PIN, BUTTON_MODULE ? INPUT : INPUT_PULLUP);
  pinMode(SERVO_PIN, OUTPUT);
  digitalWrite(SERVO_PIN, LOW);
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
  Serial.begin(UART_BAUD);
  delay(200); // Keep the button released during power-up/reset.
  releasedLevel = static_cast<uint8_t>(digitalRead(BUTTON_PIN));
  rawButton = stableButton = releasedLevel;
  buttonChangedAt = lastWaitingAt = millis();
  Serial.println(F("GILJABI UART DIAGNOSTIC v1 / 9600 8N1"));
  Serial.println(F("HM-10 TXD -> D0; HM-10 RXD MUST BE DISCONNECTED"));
  Serial.println(F("D3 button = local servo test, not ASSIST"));
  Serial.println(F("READY: press phone TEST; do not type in this monitor"));
}

void loop() {
  uint8_t sample = static_cast<uint8_t>(digitalRead(BUTTON_PIN));
  if (sample != rawButton) {rawButton = sample; buttonChangedAt = millis();}
  if (sample != stableButton && millis() - buttonChangedAt >= 40UL) {
    stableButton = sample;
    if (stableButton != releasedLevel) {
      Serial.println(F("BUTTON: local servo test"));
      startWarning();
    }
  }
  // Limit a noisy UART to 32 bytes per loop so the button/servo still run.
  for (uint8_t count = 0; count < 32 && Serial.available(); ++count) {
    receiveByte(static_cast<uint8_t>(Serial.read()));
  }
  updateWarning();
  if ((traceUsed || traceClipped) && millis() - lastRxAt >= 250UL) {
    dumpTrace();
    Serial.println(F("FRAME: waiting for newline; bytes alone do not start servo"));
  }
  if (!everReceived && millis() - lastWaitingAt >= 5000UL) {
    lastWaitingAt = millis();
    Serial.println(F("WAITING: no UART bytes received yet"));
  }
}
