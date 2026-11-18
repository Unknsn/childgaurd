#pragma once

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"
#include "child_profile.h"
#include "node_config.h"
#include "tamper_monitor.h"

class SafeBandBleManager {
public:
    static void init();
    static void updateAdvertisement(NodeState state, uint32_t timestampMs = 0, uint8_t alertType = ALERT_TYPE_NORMAL);
    static void notifyStatus(NodeState state, uint8_t alertType = ALERT_TYPE_NORMAL);
    static void notifyTamperStatus(TamperState state);
    static void notifyProvisionStatus(const char* status);
    static bool isDeviceConnected();
    static void setDeviceConnected(bool connected);
    static void handleLoop();

    // Handlers for characteristic write operations
    static void handleProvisionWrite(const String& value);
    static void handleMaintenanceWrite(const String& value);

private:
    static void buildManufacturerPayload(
        uint8_t* outBuffer,
        size_t outSize,
        const String& ephemeralId,
        RiskLevelCode risk,
        uint32_t timeSeconds,
        float lat,
        float lon,
        uint8_t alertType = ALERT_TYPE_NORMAL
    );

    static BLEServer* s_pServer;
    static BLECharacteristic* s_pStatusCharacteristic;
    static BLECharacteristic* s_pProvisionCharacteristic;
    static BLECharacteristic* s_pMaintenanceCharacteristic;
    static BLECharacteristic* s_pTamperCharacteristic;
    static BLEAdvertising* s_pAdvertising;
    static bool s_deviceConnected;
    static bool s_oldDeviceConnected;
    static uint32_t s_lastAdvUpdateMs;
    static String s_currentEphemeralId;
    static uint8_t s_currentAlertType;
};
