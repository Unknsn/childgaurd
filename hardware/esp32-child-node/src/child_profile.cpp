#include "child_profile.h"

// Default local profile - NEVER broadcast publicly over BLE
ChildProfile SafeBandNodeIdentity::s_localProfile = {
    "CHILD-001",            // childId
    "Demo Child",           // displayName
    8,                      // age
    "O+",                   // bloodGroup
    "+91 98765 43210",      // emergencyContactPhone
    "SB-CHLD"               // deviceId (permanent local ID)
};

NodeState SafeBandNodeIdentity::s_currentState = NODE_INITIALIZING;

void SafeBandNodeIdentity::init(const char* permanentDeviceId) {
    if (permanentDeviceId && strlen(permanentDeviceId) > 0) {
        strncpy(s_localProfile.deviceId, permanentDeviceId, sizeof(s_localProfile.deviceId) - 1);
        s_localProfile.deviceId[sizeof(s_localProfile.deviceId) - 1] = '\0';
    }
    s_currentState = NODE_NORMAL;
}

const char* SafeBandNodeIdentity::getPermanentDeviceId() {
    return s_localProfile.deviceId;
}

const ChildProfile& SafeBandNodeIdentity::getLocalProfile() {
    return s_localProfile;
}

void SafeBandNodeIdentity::setLocalProfile(const ChildProfile& profile) {
    s_localProfile = profile;
}

NodeState SafeBandNodeIdentity::getState() {
    return s_currentState;
}

void SafeBandNodeIdentity::setState(NodeState newState) {
    s_currentState = newState;
}

const char* SafeBandNodeIdentity::getStateName(NodeState state) {
    switch (state) {
        case NODE_INITIALIZING:   return "INITIALIZING";
        case NODE_NORMAL:         return "NORMAL";
        case NODE_SOS_COUNTDOWN:  return "SOS_COUNTDOWN";
        case NODE_SOS_ACTIVE:     return "SOS_ACTIVE";
        case NODE_ERROR:          return "ERROR";
        default:                  return "UNKNOWN";
    }
}

/**
 * Computes exact Java String.hashCode() with 32-bit signed wrap-around.
 * Matches Android BleSafetyManager.kt generateEphemeralId logic identically.
 */
int32_t SafeBandNodeIdentity::computeJavaHashCode(const String& s) {
    uint32_t h = 0;
    for (size_t i = 0; i < s.length(); i++) {
        h = 31u * h + (uint32_t)(uint8_t)s[i];
    }
    return (int32_t)h;
}

/**
 * Generates privacy-preserving rotating ephemeral identifier ("EP-XXXX").
 * Rotates every 15 minutes (EPHEMERAL_WINDOW_MS = 900,000 ms).
 * Public BLE broadcast uses this identifier rather than child or device identity.
 */
String SafeBandNodeIdentity::getEphemeralId(uint32_t currentMillis) {
    if (currentMillis == 0) {
        currentMillis = millis();
    }
    uint32_t epochWindow = currentMillis / EPHEMERAL_WINDOW_MS;
    String combined = String(s_localProfile.deviceId) + "-" + String(epochWindow);

    int32_t signedHash = computeJavaHashCode(combined);
    // Kotlin: hashCode().absoluteValue % 65536
    int32_t absHash = signedHash;
    if (absHash < 0) {
        // Protect against INT32_MIN overflow
        absHash = (absHash == INT32_MIN) ? 0 : -absHash;
    }
    uint32_t hexVal = ((uint32_t)absHash) % 65536u;

    char buf[12];
    snprintf(buf, sizeof(buf), "EP-%04X", (unsigned int)hexVal);
    return String(buf);
}
