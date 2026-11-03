#include <cassert>
#include <iostream>
#include "../hm10_sg90_button.ino"

void advance(unsigned long ms){fakeMillis+=ms;loop();}
void changeButton(uint8_t value){buttonInput=value;loop();advance(41);}
void boot(uint8_t released){
    fakeMillis=0;buttonInput=released;
    hm10.output.clear();hm10.input.clear();
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
void largerGestureAndResponsiveButton(uint8_t released){
    boot(released);hm10.feed("OBSTACLE\r\n");loop();unsigned long start=fakeMillis;
    assert(moving&&warningServo.attached);
    changeButton(released==HIGH?LOW:HIGH);
    assert(moving);assert(hm10.output=="ASSIST\n");
    fakeMillis=start+179;loop();assert(warningServo.angles.size()==1);
    fakeMillis=start+180;loop();
    hm10.feed("TEST\nOBSTACLE\n"); // Alerts during motion must not queue another gesture.
    for(unsigned long at:{400UL,620UL,840UL,1060UL,1280UL,1500UL}){fakeMillis=start+at;loop();}
    fakeMillis=start+1739;loop();assert(moving&&warningServo.attached);
    fakeMillis=start+1740;loop();
    assert(!moving&&!warningServo.attached);
    assert((warningServo.angles==std::vector<uint8_t>{90,45,135,45,135,45,135,90}));
    for(uint8_t angle:warningServo.angles)assert(angle>=45&&angle<=135);
    assert(hm10.output=="ASSIST\n");advance(2000);assert(hm10.output=="ASSIST\n");
    assert(warningServo.angles.size()==8&&!moving);
}
void commandScenario(){
    boot(HIGH);hm10.feed("UNKNOWN\n");loop();assert(!moving);
    hm10.feed(std::string(80,'X')+"TEST\n");loop();assert(!moving);
    hm10.feed("TE");loop();assert(!moving);hm10.feed("ST\n");loop();assert(moving);
}
int main(){
    buttonScenario(HIGH);buttonScenario(LOW);
    largerGestureAndResponsiveButton(HIGH);largerGestureAndResponsiveButton(LOW);
    commandScenario();
    std::cout<<"Passed firmware scenarios: both button polarities, debounce, hold/release, bounded triple sweep, live button during motion, UART framing.\n";
}
