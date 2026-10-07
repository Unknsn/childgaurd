#pragma once

#include <Arduino.h>
#include "config.h"

// Node operational states
enum NodeState {
    NODE_INITIALIZING = 0,
    NODE_NORMAL = 1,
    NODE_SOS_COUNTDOWN = 2,
    NODE_SOS_ACTIVE = 3,
    NODE_ERROR = 4
};

// SafeBand risk levels (matching Android RiskLevel enum)
enum RiskLevelCode {
    RISK_NORMAL = 0,
    RISK_LOW = 1,
    RISK_MEDIUM = 2,
    RISK_HIGH = 3,
    RISK_CRITICAL = 4
};

// Local-only Child Profile (NEVER broadcasted over public BLE)
struct ChildProfile {
    char childId[16];
    char displayName[32];
    int age;
    char bloodGroup[8];
    char emergencyContactPhone[20];
    char deviceId[16];
};

// Node Identity & Ephemeral ID Manager
class SafeBandNodeIdentity {
public:
    static void init(const char* permanentDeviceId);
    static const char* getPermanentDeviceId();
    static String getEphemeralId(uint32_t currentMillis = 0);
    static const ChildProfile& getLocalProfile();
    static void setLocalProfile(const ChildProfile& profile);

    static NodeState getState();
    static void setState(NodeState newState);
    static const char* getStateName(NodeState state);

    static int32_t computeJavaHashCode(const String& s);

private:
    static ChildProfile s_localProfile;
    static NodeState s_currentState;
};
