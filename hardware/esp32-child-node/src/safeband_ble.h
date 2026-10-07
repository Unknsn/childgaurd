#pragma once

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"
#include "child_profile.h"

class SafeBandBleManager {
public:
    static void init();
    static void updateAdvertisement(NodeState state, uint32_t timestampMs = 0);
    static void notifyStatus(NodeState state);
    static bool isDeviceConnected();
    static void setDeviceConnected(bool connected);
    static void handleLoop();

private:
    static void buildManufacturerPayload(uint8_t* outBuffer, size_t outSize, const String& ephemeralId, RiskLevelCode risk, uint32_t timeSeconds, float lat, float lon);

    static BLEServer* s_pServer;
    static BLECharacteristic* s_pStatusCharacteristic;
    static BLEAdvertising* s_pAdvertising;
    static bool s_deviceConnected;
    static bool s_oldDeviceConnected;
    static uint32_t s_lastAdvUpdateMs;
    static String s_currentEphemeralId;
};
