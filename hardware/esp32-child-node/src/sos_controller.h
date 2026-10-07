#pragma once

#include <Arduino.h>
#include "config.h"
#include "child_profile.h"

typedef void (*SosStateCallback)(NodeState oldState, NodeState newState);

class SosController {
public:
    static void init(SosStateCallback callback = nullptr);
    static void update();
    static bool isSosActive();
    static void clearSos();

private:
    static SosStateCallback s_callback;
    static bool s_lastButtonRaw;
    static uint32_t s_pressStartMs;
    static bool s_isPressed;
    static bool s_sosConfirmedThisPress;
};
