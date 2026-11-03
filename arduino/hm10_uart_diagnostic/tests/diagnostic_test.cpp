#include <cassert>
#include <iostream>
#include <string>
#define F(value) value
#include "../hm10_uart_diagnostic.ino"

void advance(unsigned long ms) {fakeMillis += ms; loop();}
void finishGesture() {
  unsigned long start = phaseAt;
  for (unsigned long at : {180UL,400UL,620UL,840UL,1060UL,1280UL,1500UL,1740UL}) {
    fakeMillis = start + at; loop();
  }
  assert(!moving && !warningServo.attached && pinValues[LED_BUILTIN] == LOW);
}
bool logged(const std::string &value) {return Serial.output.find(value) != std::string::npos;}

int main(int argc, char **argv) {
  assert(argc == 2);
  buttonInput = std::string(argv[1]) == "HIGH" ? HIGH : LOW;
  uint8_t released = buttonInput, pressed = released == HIGH ? LOW : HIGH;
  setup(); loop();
  assert(logged("READY") && !moving && warningServo.angles.empty());
  advance(5000);
  assert(logged("WAITING: no UART bytes received yet"));
  Serial.output.clear();

  // Preserve non-ASCII bytes in the hex trace, never treat them as a command.
  Serial.feed(std::string("\x80\xff", 2)); loop(); advance(250);
  assert(logged("RX HEX: 80 FF"));
  assert(logged("FRAME: waiting for newline"));
  Serial.feed("TEST\n"); loop();
  assert(logged("COMMAND: rejected") && !moving);

  // Idle trace reporting must not throw away fragmented ASCII commands.
  Serial.output.clear();
  Serial.feed("TE"); loop(); advance(251);
  assert(logged("RX HEX: 54 45") && !moving);
  Serial.feed("ST\r\n"); loop();
  assert(logged("RX HEX: 53 54 0D 0A") && logged("COMMAND: valid"));
  assert(moving && pinValues[LED_BUILTIN] == HIGH);
  Serial.feed("TEST\n"); loop();
  assert(logged("already moving"));
  finishGesture();
  assert((warningServo.angles == std::vector<uint8_t>{90,45,135,45,135,45,135,90}));

  // Garbage and a command embedded in an oversized frame cannot move the servo.
  Serial.output.clear();
  Serial.feed(std::string(80, 'X') + "TEST\n");
  while (Serial.available()) loop();
  assert(logged("trace limited to 32 bytes") && logged("COMMAND: rejected") && !moving);
  Serial.feed("UNKNOWN\n"); loop();
  assert(logged("unrecognized") && !moving);

  // The physical button tests the motor locally for either module polarity.
  Serial.output.clear();
  buttonInput = pressed; loop(); advance(10);
  assert(!moving);
  advance(31);
  assert(moving && logged("BUTTON: local servo test"));
  finishGesture(); advance(2000);
  assert(warningServo.angles.size() == 16);
  buttonInput = released; loop(); advance(41);
  assert(!moving && warningServo.angles.size() == 16);
  assert(!logged("ASSIST\n"));

  // A complete phone frame has the documented byte trace and still works later.
  Serial.output.clear();
  Serial.feed("TEST\n"); loop();
  assert(logged("RX HEX: 54 45 53 54 0A") && logged("COMMAND: valid") && moving);
  finishGesture();
  assert(warningServo.angles.size() == 24);
  std::cout << "Passed RX trace, partial frames, invalid data, coalescing and local button (released " << argv[1] << ").\n";
}
