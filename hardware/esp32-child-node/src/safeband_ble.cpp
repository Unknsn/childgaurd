#include "safeband_ble.h"
#include "status_led.h"

BLEServer* SafeBandBleManager::s_pServer = nullptr;
BLECharacteristic* SafeBandBleManager::s_pStatusCharacteristic = nullptr;
BLEAdvertising* SafeBandBleManager::s_pAdvertising = nullptr;
bool SafeBandBleManager::s_deviceConnected = false;
bool SafeBandBleManager::s_oldDeviceConnected = false;
uint32_t SafeBandBleManager::s_lastAdvUpdateMs = 0;
String SafeBandBleManager::s_currentEphemeralId = "";

class SafeBandServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        SafeBandBleManager::setDeviceConnected(true);
        StatusLed::flashBleConnected();
        Serial.println("[BLE] Guardian device connected!");
    }

    void onDisconnect(BLEServer* pServer) override {
        SafeBandBleManager::setDeviceConnected(false);
        Serial.println("[BLE] Guardian device disconnected. Auto-resuming advertising...");
        pServer->startAdvertising();
    }
};

void SafeBandBleManager::setDeviceConnected(bool connected) {
    s_deviceConnected = connected;
}

bool SafeBandBleManager::isDeviceConnected() {
    return s_deviceConnected;
}

void SafeBandBleManager::buildManufacturerPayload(
    uint8_t* outBuffer,
    size_t outSize,
    const String& ephemeralId,
    RiskLevelCode risk,
    uint32_t timeSeconds,
    float lat,
    float lon
) {
    if (outSize < 24) return;
    memset(outBuffer, 0, outSize);

    // 1. Manufacturer Company ID: 0x5AFE (little-endian: 0xFE, 0x5A)
    outBuffer[0] = (uint8_t)(SAFEBAND_MANUFACTURER_ID & 0xFF);
    outBuffer[1] = (uint8_t)((SAFEBAND_MANUFACTURER_ID >> 8) & 0xFF);

    // 2. SafeBand Magic Bytes: 'S' (0x53), 'B' (0x42)
    outBuffer[2] = SAFEBAND_MAGIC_BYTE_1;
    outBuffer[3] = SAFEBAND_MAGIC_BYTE_2;

    // 3. Ephemeral Node ID (7 ASCII bytes padded with spaces)
    for (size_t i = 0; i < 7; i++) {
        outBuffer[4 + i] = (i < ephemeralId.length()) ? (uint8_t)ephemeralId[i] : (uint8_t)' ';
    }

    // 4. Risk Level byte (0 = NORMAL, 4 = CRITICAL)
    outBuffer[11] = (uint8_t)risk;

    // 5. Timestamp (4 bytes, big-endian)
    outBuffer[12] = (uint8_t)((timeSeconds >> 24) & 0xFF);
    outBuffer[13] = (uint8_t)((timeSeconds >> 16) & 0xFF);
    outBuffer[14] = (uint8_t)((timeSeconds >> 8) & 0xFF);
    outBuffer[15] = (uint8_t)(timeSeconds & 0xFF);

    // 6. Latitude (4 bytes, big-endian float: 0.0f = no GPS hardware)
    union { float f; uint8_t b[4]; } latBytes;
    latBytes.f = lat;
    outBuffer[16] = latBytes.b[3];
    outBuffer[17] = latBytes.b[2];
    outBuffer[18] = latBytes.b[1];
    outBuffer[19] = latBytes.b[0];

    // 7. Longitude (4 bytes, big-endian float: 0.0f = no GPS hardware)
    union { float f; uint8_t b[4]; } lonBytes;
    lonBytes.f = lon;
    outBuffer[20] = lonBytes.b[3];
    outBuffer[21] = lonBytes.b[2];
    outBuffer[22] = lonBytes.b[1];
    outBuffer[23] = lonBytes.b[0];
}

void SafeBandBleManager::init() {
    Serial.println("[BLE] Initializing SafeBand BLE subsystem...");

    // Initialize BLE device with public name
    BLEDevice::init(SAFEBAND_BLE_DEVICE_NAME);

    // Set TX power to maximum for stable transmission
    BLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create GATT Server
    s_pServer = BLEDevice::createServer();
    s_pServer->setCallbacks(new SafeBandServerCallbacks());

    // Create Primary SafeBand Service
    BLEService* pService = s_pServer->createService(SAFEBAND_SERVICE_UUID);

    // Create Status / Telemetry Characteristic
    s_pStatusCharacteristic = pService->createCharacteristic(
        SAFEBAND_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pStatusCharacteristic->addDescriptor(new BLE2902());

    // Initial status value
    s_pStatusCharacteristic->setValue("NORMAL");

    pService->start();

    // Setup initial advertising
    s_pAdvertising = BLEDevice::getAdvertising();
    s_pAdvertising->addServiceUUID(SAFEBAND_SERVICE_UUID);
    s_pAdvertising->setScanResponse(false);
    s_pAdvertising->setMinPreferred(0x06);
    s_pAdvertising->setMinPreferred(0x12);

    updateAdvertisement(NODE_NORMAL, millis());

    s_pAdvertising->start();
    Serial.println("[BLE] SafeBand BLE advertising active!");
    Serial.printf("[BLE] Service UUID: %s\n", SAFEBAND_SERVICE_UUID);
    Serial.printf("[BLE] Device Name : %s\n", SAFEBAND_BLE_DEVICE_NAME);
}

void SafeBandBleManager::updateAdvertisement(NodeState state, uint32_t timestampMs) {
    if (!s_pAdvertising) return;

    if (timestampMs == 0) {
        timestampMs = millis();
    }

    s_currentEphemeralId = SafeBandNodeIdentity::getEphemeralId(timestampMs);

    RiskLevelCode risk = (state == NODE_SOS_ACTIVE) ? RISK_CRITICAL : RISK_NORMAL;
    uint32_t timeSeconds = timestampMs / 1000UL;
    float lat = 0.0f; // No GPS hardware connected
    float lon = 0.0f; // No GPS hardware connected

    uint8_t payloadBytes[24];
    buildManufacturerPayload(payloadBytes, sizeof(payloadBytes), s_currentEphemeralId, risk, timeSeconds, lat, lon);

    std::string mfString((char*)payloadBytes, 24);

    BLEAdvertisementData advData;
    advData.setFlags(0x06); // General Discoverable + BR/EDR Not Supported
    advData.setCompleteServices(BLEUUID(SAFEBAND_SERVICE_UUID));
    advData.setManufacturerData(mfString);

    s_pAdvertising->stop();
    s_pAdvertising->setAdvertisementData(advData);
    s_pAdvertising->start();

    s_lastAdvUpdateMs = timestampMs;

    Serial.printf("[BLE] Advertisement updated: Ephemeral ID=%s, Risk=%s, State=%s\n",
        s_currentEphemeralId.c_str(),
        (risk == RISK_CRITICAL) ? "CRITICAL (SOS)" : "NORMAL",
        SafeBandNodeIdentity::getStateName(state)
    );

    notifyStatus(state);
}

void SafeBandBleManager::notifyStatus(NodeState state) {
    if (s_pStatusCharacteristic && s_deviceConnected) {
        const char* statusStr = (state == NODE_SOS_ACTIVE) ? "SOS_CRITICAL" : "NORMAL";
        s_pStatusCharacteristic->setValue(statusStr);
        s_pStatusCharacteristic->notify();
    }
}

void SafeBandBleManager::handleLoop() {
    uint32_t now = millis();

    // Check for ephemeral ID rotation (every 15 minutes)
    if (now - s_lastAdvUpdateMs >= EPHEMERAL_WINDOW_MS) {
        Serial.println("[BLE] Rotating ephemeral identifier...");
        updateAdvertisement(SafeBandNodeIdentity::getState(), now);
    }

    // Handle connection state transitions
    if (!s_deviceConnected && s_oldDeviceConnected) {
        delay(100); // Bluetooth stack stabilize
        s_pServer->startAdvertising();
        s_oldDeviceConnected = s_deviceConnected;
    }
    if (s_deviceConnected && !s_oldDeviceConnected) {
        s_oldDeviceConnected = s_deviceConnected;
    }
}
