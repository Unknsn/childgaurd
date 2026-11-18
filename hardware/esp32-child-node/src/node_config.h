#pragma once

#include <Arduino.h>
#include <Preferences.h>
#include "config.h"

class NodeConfig {
public:
    static void init();
    static bool isProvisioned();
    static String getChildId();
    static String getParentId();
    static String getParentNodeIp();
    static int32_t getParentNodePort();
    static String getParentNodeEndpointId();
    static String getAuthToken();
    static int32_t getConfigVersion();
    static uint32_t getTamperAuthorizedUntil();

    static bool saveProvisioning(
        const String& childId,
        const String& parentId,
        const String& parentNodeIp,
        int32_t parentNodePort,
        const String& parentNodeEndpointId,
        const String& authToken,
        int32_t configVersion
    );

    static void setTamperAuthorizedUntil(uint32_t expiryEpochOrMs);
    static void clearConfiguration();
    static bool verifyAuthToken(const String& token);

private:
    static Preferences s_prefs;
    static bool s_isProvisioned;
    static String s_childId;
    static String s_parentId;
    static String s_parentNodeIp;
    static int32_t s_parentNodePort;
    static String s_parentNodeEndpointId;
    static String s_authToken;
    static int32_t s_configVersion;
    static uint32_t s_tamperAuthorizedUntil;
};
