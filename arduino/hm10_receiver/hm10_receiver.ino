// SPDX-License-Identifier: AGPL-3.0-only
// UNO / classic Nano (ATmega328P) + external HM-10 UART module.
// See README.md for wiring and voltage levels. No motor is driven by this example.
#include <SoftwareSerial.h>
#include <string.h>

SoftwareSerial hm10(10, 11); // Arduino RX, TX
char command[32];
uint8_t used = 0;
bool discardLine = false;
bool ledOn = false;
unsigned long ledUntil = 0;

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
  Serial.begin(115200);
  hm10.begin(9600); // Match the actual HM-10 UART baud rate.
  Serial.println("HM-10 receiver ready");
}

void loop() {
  while (hm10.available()) {
    char c = static_cast<char>(hm10.read());
    if (c == '\r') continue;
    if (c == '\n') {
      if (!discardLine) {
        command[used] = '\0';
        if (strcmp(command, "TEST") == 0 || strcmp(command, "OBSTACLE") == 0) {
          Serial.print("Received: ");
          Serial.println(command);
          digitalWrite(LED_BUILTIN, HIGH);
          ledOn = true;
          ledUntil = millis() + (strcmp(command, "TEST") == 0 ? 200UL : 700UL);
        }
      }
      used = 0;
      discardLine = false;
    } else if (!discardLine) {
      if (used < sizeof(command) - 1) command[used++] = c;
      else { discardLine = true; used = 0; }
    }
  }
  if (ledOn && static_cast<long>(millis() - ledUntil) >= 0) {
    digitalWrite(LED_BUILTIN, LOW);
    ledOn = false;
  }
}
