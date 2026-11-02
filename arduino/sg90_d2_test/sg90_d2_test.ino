// SPDX-License-Identifier: AGPL-3.0-only
// Temporary servo-only test. Upload the main v3 firmware again after testing.
#include <Servo.h>
Servo testServo;
void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  testServo.attach(2);
  testServo.write(90);
  delay(1500);
}
void loop() {
  digitalWrite(LED_BUILTIN, HIGH);
  testServo.write(60);
  delay(2000);
  digitalWrite(LED_BUILTIN, LOW);
  testServo.write(120);
  delay(2000);
  testServo.write(90);
  delay(2000);
}
