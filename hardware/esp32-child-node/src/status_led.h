#pragma once

#include <Arduino.h>
#include "config.h"
#include "child_profile.h"

class StatusLed {
public:
    static void init();
    static void update(NodeState state);
    static void flashBleConnected();
    static void setRawColor(uint8_t r, uint8_t g, uint8_t b);

private:
    static void writeRgb(uint8_t r, uint8_t g, uint8_t b);
    static uint32_t s_bleFlashUntilMs;
};
