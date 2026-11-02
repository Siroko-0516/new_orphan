#include <cassert>
#include <iostream>
#include "../hm10_sg90_button.ino"

void advance(unsigned long ms){fakeMillis+=ms;loop();}
void changeButton(uint8_t value){buttonInput=value;loop();advance(41);}
void boot(uint8_t released){
    fakeMillis=0;buttonInput=released;
    hm10.output.clear();hm10.input.clear();hm10.listening=true;
    Serial.output.clear();warningServo.angles.clear();warningServo.attached=false;
    moving=false;assistantPending=false;used=0;discardLine=false;
    setup();loop();advance(50);
    assert(pinModes[BUTTON_PIN]==INPUT);
    assert(hm10.output.empty());assert(warningServo.angles.empty());
    assert(releasedLevel==released);
}
void buttonScenario(uint8_t released){
    boot(released);uint8_t pressed=released==HIGH?LOW:HIGH;
    buttonInput=pressed;loop();advance(10);assert(hm10.output.empty());
    advance(31);assert(hm10.output=="ASSIST\n");
    advance(1500);assert(hm10.output=="ASSIST\n");
    changeButton(released);assert(hm10.output=="ASSIST\n");
    changeButton(pressed);assert(hm10.output=="ASSIST\nASSIST\n");
}
void gestureAndDeferredButton(uint8_t released){
    boot(released);hm10.feed("OBSTACLE\r\n");loop();unsigned long start=fakeMillis;
    assert(moving&&warningServo.attached&&!hm10.listening);
    changeButton(released==HIGH?LOW:HIGH);assert(hm10.output.empty());
    fakeMillis=start+180;loop();fakeMillis=start+340;loop();fakeMillis=start+500;loop();
    assert(hm10.output.empty());fakeMillis=start+760;loop();
    assert(!moving&&!warningServo.attached&&hm10.listening);
    assert((warningServo.angles==std::vector<uint8_t>{90,80,100,90}));
    assert(hm10.output=="ASSIST\n");advance(2000);assert(hm10.output=="ASSIST\n");
}
void commandScenario(){
    boot(HIGH);hm10.feed("UNKNOWN\n");loop();assert(!moving);
    hm10.feed(std::string(80,'X')+"TEST\n");loop();assert(!moving);
    hm10.feed("TE");loop();assert(!moving);hm10.feed("ST\n");loop();assert(moving);
}
int main(){
    buttonScenario(HIGH);buttonScenario(LOW);
    gestureAndDeferredButton(HIGH);gestureAndDeferredButton(LOW);
    commandScenario();
    std::cout<<"Passed firmware scenarios: both button polarities, debounce, hold/release, motor phases, deferred button, UART framing.\n";
}
