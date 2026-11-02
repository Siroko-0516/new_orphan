#pragma once
#include "Arduino.h"
class SoftwareSerial:public Stream {
public:
    bool listening=true;
    SoftwareSerial(uint8_t,uint8_t){}
    bool stopListening(){listening=false;input.clear();return true;}
    bool listen(){listening=true;return true;}
};
