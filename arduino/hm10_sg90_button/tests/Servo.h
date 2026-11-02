#pragma once
#include <cstdint>
#include <vector>
class Servo {
public:
    bool attached=false;std::vector<uint8_t> angles;
    void attach(uint8_t){attached=true;}
    void write(uint8_t angle){angles.push_back(angle);}
    void detach(){attached=false;}
};
