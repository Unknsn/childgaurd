#include "safeband_ble.h"
#include "status_led.h"
#include "node_config.h"
#include "tamper_monitor.h"

BLEServer* SafeBandBleManager::s_pServer = nullptr;
BLECharacteristic* SafeBandBleManager::s_pStatusCharacteristic = nullptr;
BLECharacteristic* SafeBandBleManager::s_pProvisionCharacteristic = nullptr;
BLECharacteristic* SafeBandBleManager::s_pMaintenanceCharacteristic = nullptr;
BLECharacteristic* SafeBandBleManager::s_pTamperCharacteristic = nullptr;
BLEAdvertising* SafeBandBleManager::s_pAdvertising = nullptr;
bool SafeBandBleManager::s_deviceConnected = false;
bool SafeBandBleManager::s_oldDeviceConnected = false;
uint32_t SafeBandBleManager::s_lastAdvUpdateMs = 0;
String SafeBandBleManager::s_currentEphemeralId = "";
uint8_t SafeBandBleManager::s_currentAlertType = ALERT_TYPE_NORMAL;

class SafeBandServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        SafeBandBleManager::setDeviceConnected(true);
        StatusLed::flashBleConnected();
        Serial.println("[BLE] Guardian device connected via GATT!");
    }

    void onDisconnect(BLEServer* pServer) override {
        SafeBandBleManager::setDeviceConnected(false);
        Serial.println("[BLE] Guardian device disconnected. Auto-resuming advertising...");
        pServer->startAdvertising();
    }
};

class ProvisionCallback: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pChar) override {
        String rxValue = pChar->getValue().c_str();
        if (rxValue.length() > 0) {
            Serial.printf("[BLE-GATT] Provisioning payload received: %s\n", rxValue.c_str());
            SafeBandBleManager::handleProvisionWrite(rxValue);
        }
    }
};

class MaintenanceCallback: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pChar) override {
        String rxValue = pChar->getValue().c_str();
        if (rxValue.length() > 0) {
            Serial.printf("[BLE-GATT] Maintenance command received: %s\n", rxValue.c_str());
            SafeBandBleManager::handleMaintenanceWrite(rxValue);
        }
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
    float lon,
    uint8_t alertType
) {
    if (outSize < 25) return;
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

    // 8. Alert Type byte (0x00 = NORMAL, 0x01/0x03 = SOS, 0x02/0x04 = TAMPER)
    outBuffer[24] = alertType;
}

void SafeBandBleManager::init() {
    Serial.println("[BLE] Initializing SafeBand BLE subsystem with Provisioning & Tamper...");

    // Initialize BLE device with public name
    BLEDevice::init(SAFEBAND_BLE_DEVICE_NAME);

    // Set TX power to maximum for stable transmission
    BLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create GATT Server
    s_pServer = BLEDevice::createServer();
    s_pServer->setCallbacks(new SafeBandServerCallbacks());

    // Create Primary SafeBand Service
    BLEService* pService = s_pServer->createService(SAFEBAND_SERVICE_UUID);

    // 1. Status / Telemetry Characteristic (Read / Notify)
    s_pStatusCharacteristic = pService->createCharacteristic(
        SAFEBAND_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pStatusCharacteristic->addDescriptor(new BLE2902());
    s_pStatusCharacteristic->setValue("NORMAL");

    // 2. Provisioning Characteristic (Write / Read / Notify)
    s_pProvisionCharacteristic = pService->createCharacteristic(
        SAFEBAND_CHAR_PROVISION_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pProvisionCharacteristic->addDescriptor(new BLE2902());
    s_pProvisionCharacteristic->setCallbacks(new ProvisionCallback());
    s_pProvisionCharacteristic->setValue(NodeConfig::isProvisioned() ? "PROVISIONED" : "UNPROVISIONED");

    // 3. Maintenance Characteristic (Write / Read / Notify)
    s_pMaintenanceCharacteristic = pService->createCharacteristic(
        SAFEBAND_CHAR_MAINTENANCE_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pMaintenanceCharacteristic->addDescriptor(new BLE2902());
    s_pMaintenanceCharacteristic->setCallbacks(new MaintenanceCallback());
    s_pMaintenanceCharacteristic->setValue("MAINT_IDLE");

    // 4. Tamper Monitoring Characteristic (Read / Notify)
    s_pTamperCharacteristic = pService->createCharacteristic(
        SAFEBAND_CHAR_TAMPER_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pTamperCharacteristic->addDescriptor(new BLE2902());
    s_pTamperCharacteristic->setValue(TamperMonitor::getStateName(TamperMonitor::getState()));

    pService->start();

    // Setup initial advertising
    s_pAdvertising = BLEDevice::getAdvertising();
    s_pAdvertising->setScanResponse(true);
    s_pAdvertising->setMinPreferred(0x06);
    s_pAdvertising->setMinPreferred(0x12);

    updateAdvertisement(NODE_NORMAL, millis(), ALERT_TYPE_NORMAL);

    Serial.println("[BLE] SafeBand BLE advertising active!");
    Serial.printf("[BLE] Service UUID     : %s\n", SAFEBAND_SERVICE_UUID);
    Serial.printf("[BLE] Status Char UUID  : %s\n", SAFEBAND_CHAR_STATUS_UUID);
    Serial.printf("[BLE] Provision UUID    : %s\n", SAFEBAND_CHAR_PROVISION_UUID);
    Serial.printf("[BLE] Maintenance UUID  : %s\n", SAFEBAND_CHAR_MAINTENANCE_UUID);
    Serial.printf("[BLE] Tamper Char UUID  : %s\n", SAFEBAND_CHAR_TAMPER_UUID);
}

void SafeBandBleManager::handleProvisionWrite(const String& value) {
    // 1. Direct Child Profile Write: CHILD:name|age|bloodGroup|emergencyPhone
    if (value.startsWith("CHILD:")) {
        String payload = value.substring(6);
        int t[3];
        int tc = 0;
        for (int i = 0; i < payload.length() && tc < 3; i++) {
            if (payload[i] == '|') t[tc++] = i;
        }
        if (tc >= 3) {
            String chName = payload.substring(0, t[0]);
            int32_t chAge = payload.substring(t[0] + 1, t[1]).toInt();
            String chBlood = payload.substring(t[1] + 1, t[2]);
            String chPhone = payload.substring(t[2] + 1);
            bool ok = NodeConfig::saveChildDetails(chName, chAge, chBlood, chPhone);
            if (ok && s_pProvisionCharacteristic) {
                s_pProvisionCharacteristic->setValue("ACK:CHILD:OK");
                s_pProvisionCharacteristic->notify();
            }
            Serial.printf("[BLE] Child details updated via BLE: %s, %d, %s, %s\n",
                chName.c_str(), chAge, chBlood.c_str(), chPhone.c_str());
            return;
        } else {
            if (s_pProvisionCharacteristic) {
                s_pProvisionCharacteristic->setValue("ERR:INVALID_CHILD_FIELDS");
                s_pProvisionCharacteristic->notify();
            }
            return;
        }
    }

    // 2. Full Provisioning Write: PROV:childId|parentId|parentNodeIp|parentNodePort|endpointId|authToken|version[|name|age|blood|phone]
    if (!value.startsWith("PROV:")) {
        if (s_pProvisionCharacteristic) {
            s_pProvisionCharacteristic->setValue("ERR:INVALID_PREFIX");
            s_pProvisionCharacteristic->notify();
        }
        return;
    }

    String payload = value.substring(5);
    int tokens[11];
    int tokenCount = 0;

    for (int i = 0; i < payload.length() && tokenCount < 10; i++) {
        if (payload[i] == '|') {
            tokens[tokenCount++] = i;
        }
    }

    if (tokenCount < 6) {
        Serial.printf("[BLE] ERR: Provisioning payload invalid field count: %d\n", tokenCount);
        if (s_pProvisionCharacteristic) {
            s_pProvisionCharacteristic->setValue("ERR:INVALID_FIELD_COUNT");
            s_pProvisionCharacteristic->notify();
        }
        return;
    }

    String childId = payload.substring(0, tokens[0]);
    String parentId = payload.substring(tokens[0] + 1, tokens[1]);
    String parentNodeIp = payload.substring(tokens[1] + 1, tokens[2]);
    int32_t port = payload.substring(tokens[2] + 1, tokens[3]).toInt();
    String endpointId = payload.substring(tokens[3] + 1, tokens[4]);
    String authToken = payload.substring(tokens[4] + 1, tokens[5]);
    
    // Version field might be followed by more tokens or be the end
    int32_t version = 1;
    if (tokenCount > 6) {
        version = payload.substring(tokens[5] + 1, tokens[6]).toInt();
    } else {
        version = payload.substring(tokens[5] + 1).toInt();
    }

    if (port <= 0) port = 8080;
    if (version <= 0) version = 1;

    // SEC-01: Security verification for provisioning updates
    if (NodeConfig::isProvisioned()) {
        if (version <= NodeConfig::getConfigVersion()) {
            Serial.printf("[BLE] ERR: Replay detected! Provisioning version %d <= current version %d\n",
                version, NodeConfig::getConfigVersion());
            if (s_pProvisionCharacteristic) {
                s_pProvisionCharacteristic->setValue("ERR:VERSION_REPLAY_REJECTED");
                s_pProvisionCharacteristic->notify();
            }
            return;
        }
        if (!NodeConfig::verifyAuthToken(authToken)) {
            Serial.println("[BLE] ERR: Unauthorized provisioning overwrite! Auth token mismatch.");
            if (s_pProvisionCharacteristic) {
                s_pProvisionCharacteristic->setValue("ERR:UNAUTHORIZED");
                s_pProvisionCharacteristic->notify();
            }
            return;
        }
    }

    bool saved = NodeConfig::saveProvisioning(
        childId, parentId, parentNodeIp, port, endpointId, authToken, version
    );

    if (saved) {
        // Optional child profile fields in PROV: payload
        if (tokenCount >= 10) {
            String chName = payload.substring(tokens[6] + 1, tokens[7]);
            int32_t chAge = payload.substring(tokens[7] + 1, tokens[8]).toInt();
            String chBlood = payload.substring(tokens[8] + 1, tokens[9]);
            String chPhone = payload.substring(tokens[9] + 1);
            NodeConfig::saveChildDetails(chName, chAge, chBlood, chPhone);
        }

        // Update local child profile
        ChildProfile prof = SafeBandNodeIdentity::getLocalProfile();
        strncpy(prof.childId, childId.c_str(), sizeof(prof.childId) - 1);
        SafeBandNodeIdentity::setLocalProfile(prof);

        String ackMsg = "ACK:PROV:OK:v=" + String(version);
        if (s_pProvisionCharacteristic) {
            s_pProvisionCharacteristic->setValue(ackMsg.c_str());
            s_pProvisionCharacteristic->notify();
        }
        Serial.printf("[BLE] Provisioning completed and acknowledged: %s\n", ackMsg.c_str());
    } else {
        if (s_pProvisionCharacteristic) {
            s_pProvisionCharacteristic->setValue("ERR:SAVE_FAILED");
            s_pProvisionCharacteristic->notify();
        }
    }
}

void SafeBandBleManager::handleMaintenanceWrite(const String& value) {
    // Format 1: MAINT:AUTH:<durationSec>:<authToken>
    // Format 2: MAINT:CANCEL
    if (value.startsWith("MAINT:AUTH:")) {
        int firstColon = 11;
        int secondColon = value.indexOf(':', firstColon);
        if (secondColon == -1) {
            if (s_pMaintenanceCharacteristic) {
                s_pMaintenanceCharacteristic->setValue("ERR:INVALID_FORMAT");
                s_pMaintenanceCharacteristic->notify();
            }
            return;
        }

        uint32_t durationSec = value.substring(firstColon, secondColon).toInt();
        String remainder = value.substring(secondColon + 1);
        String token = remainder;
        uint32_t seq = 0;
        int thirdColon = remainder.indexOf(':');
        if (thirdColon != -1) {
            token = remainder.substring(0, thirdColon);
            int fourthColon = remainder.indexOf(':', thirdColon + 1);
            if (fourthColon != -1) {
                seq = remainder.substring(thirdColon + 1, fourthColon).toInt();
            } else {
                seq = remainder.substring(thirdColon + 1).toInt();
            }
        }

        bool ok = TamperMonitor::authorizeMaintenance(durationSec, token, seq);
        if (ok) {
            String ack = "ACK:MAINT:OK:" + String(durationSec);
            if (s_pMaintenanceCharacteristic) {
                s_pMaintenanceCharacteristic->setValue(ack.c_str());
                s_pMaintenanceCharacteristic->notify();
            }
            notifyTamperStatus(TamperMonitor::getState());
        } else {
            if (s_pMaintenanceCharacteristic) {
                s_pMaintenanceCharacteristic->setValue("ERR:MAINT:UNAUTHORIZED");
                s_pMaintenanceCharacteristic->notify();
            }
        }
    } else if (value == "MAINT:CANCEL") {
        TamperMonitor::cancelMaintenance();
        if (s_pMaintenanceCharacteristic) {
            s_pMaintenanceCharacteristic->setValue("ACK:MAINT:CANCELLED");
            s_pMaintenanceCharacteristic->notify();
        }
        notifyTamperStatus(TamperMonitor::getState());
    } else {
        if (s_pMaintenanceCharacteristic) {
            s_pMaintenanceCharacteristic->setValue("ERR:UNKNOWN_COMMAND");
            s_pMaintenanceCharacteristic->notify();
        }
    }
}

void SafeBandBleManager::updateAdvertisement(NodeState state, uint32_t timestampMs, uint8_t alertType) {
    if (!s_pAdvertising) return;

    if (timestampMs == 0) {
        timestampMs = millis();
    }

    s_currentEphemeralId = SafeBandNodeIdentity::getEphemeralId(timestampMs);
    s_currentAlertType = alertType;

    RiskLevelCode risk = (state == NODE_SOS_ACTIVE || alertType == ALERT_TYPE_TAMPER) ? RISK_CRITICAL : RISK_NORMAL;
    uint32_t timeSeconds = timestampMs / 1000UL;
    float lat = 0.0f; // No GPS hardware connected
    float lon = 0.0f; // No GPS hardware connected

    uint8_t payloadBytes[25];
    buildManufacturerPayload(payloadBytes, sizeof(payloadBytes), s_currentEphemeralId, risk, timeSeconds, lat, lon, alertType);

    std::string mfString((char*)payloadBytes, 25);

    // 1. Primary Advertising Packet: MUST stay <= 31 bytes!
    BLEAdvertisementData advData;
    advData.setFlags(0x06); // General Discoverable + BR/EDR Not Supported
    advData.setManufacturerData(mfString);

    // 2. Scan Response Packet: MUST stay <= 31 bytes!
    BLEAdvertisementData scanResponseData;
    scanResponseData.setCompleteServices(BLEUUID(SAFEBAND_SERVICE_UUID));
    scanResponseData.setShortName("SafeBand");

    s_pAdvertising->stop();
    s_pAdvertising->setAdvertisementData(advData);
    s_pAdvertising->setScanResponseData(scanResponseData);
    s_pAdvertising->setScanResponse(true);
    s_pAdvertising->start();

    s_lastAdvUpdateMs = timestampMs;

    Serial.printf("[BLE] Adv updated: Ephemeral=%s, Risk=%s, AlertType=%d, State=%s\n",
        s_currentEphemeralId.c_str(),
        (risk == RISK_CRITICAL) ? "CRITICAL" : "NORMAL",
        alertType,
        SafeBandNodeIdentity::getStateName(state)
    );

    notifyStatus(state, alertType);
}

void SafeBandBleManager::notifyStatus(NodeState state, uint8_t alertType) {
    if (s_pStatusCharacteristic && s_deviceConnected) {
        const char* statusStr = "NORMAL";
        if (alertType == ALERT_TYPE_TAMPER) {
            statusStr = "TAMPER_CRITICAL";
        } else if (state == NODE_SOS_ACTIVE) {
            statusStr = "SOS_CRITICAL";
        }
        s_pStatusCharacteristic->setValue(statusStr);
        s_pStatusCharacteristic->notify();
    }
}

void SafeBandBleManager::notifyTamperStatus(TamperState state) {
    if (s_pTamperCharacteristic && s_deviceConnected) {
        const char* statusStr = TamperMonitor::getStateName(state);
        s_pTamperCharacteristic->setValue(statusStr);
        s_pTamperCharacteristic->notify();
    }
}

void SafeBandBleManager::notifyProvisionStatus(const char* status) {
    if (s_pProvisionCharacteristic && s_deviceConnected) {
        s_pProvisionCharacteristic->setValue(status);
        s_pProvisionCharacteristic->notify();
    }
}

void SafeBandBleManager::handleLoop() {
    uint32_t now = millis();

    // Check for ephemeral ID rotation (every 15 minutes)
    if (now - s_lastAdvUpdateMs >= EPHEMERAL_WINDOW_MS) {
        Serial.println("[BLE] Rotating ephemeral identifier...");
        updateAdvertisement(SafeBandNodeIdentity::getState(), now, s_currentAlertType);
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
