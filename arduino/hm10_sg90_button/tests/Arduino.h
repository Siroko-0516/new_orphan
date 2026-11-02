#pragma once
#include <cstdint>
#include <deque>
#include <sstream>
#include <string>

constexpr uint8_t LOW=0,HIGH=1,INPUT=0,OUTPUT=1,INPUT_PULLUP=2,LED_BUILTIN=13;
inline unsigned long fakeMillis=0;
inline uint8_t buttonInput=HIGH,pinModes[32]={},pinValues[32]={};
inline unsigned long millis(){return fakeMillis;}
inline void delay(unsigned long ms){fakeMillis+=ms;}
inline void pinMode(uint8_t pin,uint8_t mode){pinModes[pin]=mode;}
inline void digitalWrite(uint8_t pin,uint8_t value){pinValues[pin]=value;}
inline int digitalRead(uint8_t){return buttonInput;}
class Stream {
public:
    std::string output;std::deque<char> input;
    void begin(unsigned long){}
    int available(){return static_cast<int>(input.size());}
    int read(){if(input.empty())return -1;char c=input.front();input.pop_front();return static_cast<unsigned char>(c);}
    template<class T> void print(const T& value){std::ostringstream s;s<<value;output+=s.str();}
    template<class T> void println(const T& value){print(value);output+='\n';}
    void feed(const std::string& value){for(char c:value)input.push_back(c);}
};
using HardwareSerial=Stream;
inline HardwareSerial Serial;
