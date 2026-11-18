#pragma once

#include <Arduino.h>
#include "config.h"

enum TamperState {
    TAMPER_INITIALIZING = 0,
    TAMPER_PROTECTED = 1,
    TAMPER_SUSPECTED = 2,
    TAMPER_CONFIRMED = 3,
    TAMPER_AUTHORIZED_DISCONNECTION = 4,
    TAMPER_RESTORING = 5,
    TAMPER_SENSOR_FAULT = 6
};

typedef void (*TamperStateCallback)(TamperState oldState, TamperState newState);

class TamperMonitor {
public:
    static void init(TamperStateCallback callback = nullptr);
    static void update();
    static TamperState getState();
    static const char* getStateName(TamperState state);
    static bool isTamperTriggered();

    static bool authorizeMaintenance(uint32_t durationSeconds, const String& token, uint32_t seq = 0);
    static void cancelMaintenance();
    static uint32_t getRemainingMaintenanceSeconds();
    static void clearTamper();

private:
    static bool performContinuityTest(bool& isFault);
    static void transitionState(TamperState newState);

    static TamperState s_currentState;
    static TamperStateCallback s_callback;
    static uint32_t s_lastCheckMs;
    static uint8_t s_consecutiveFailures;
    static uint8_t s_consecutiveSuccesses;
    static uint32_t s_maintenanceExpiryMs;
    static uint32_t s_lastMaintenanceSeq;
};
