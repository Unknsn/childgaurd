#include <Arduino.h>
#include "config.h"
#include "child_profile.h"
#include "status_led.h"
#include "sos_controller.h"
#include "safeband_ble.h"

// Callback when SOS state transitions
void onSosStateChanged(NodeState oldState, NodeState newState) {
    uint32_t now = millis();
    if (newState == NODE_SOS_ACTIVE) {
        Serial.println("[NODE] Event: SOS Activated! Broadcasting CRITICAL risk beacon...");
        SafeBandBleManager::updateAdvertisement(NODE_SOS_ACTIVE, now);
    } else if (newState == NODE_NORMAL && oldState != NODE_NORMAL) {
        Serial.println("[NODE] Event: SOS Cleared / Safe State Restored. Broadcasting NORMAL risk beacon...");
        SafeBandBleManager::updateAdvertisement(NODE_NORMAL, now);
    }
}

void printBootBanner() {
    Serial.println();
    Serial.println("=================================");
    Serial.println(" SafeBand Guardian Child Node");
    Serial.println("=================================");
    Serial.println();
    Serial.println("Chip:");
    Serial.println("ESP32-S3");
    Serial.println();
    Serial.println("Firmware:");
    Serial.println(SAFEBAND_FIRMWARE_VERSION);
    Serial.println();
    Serial.println("Protocol:");
    Serial.println("SafeBand BLE V2");
    Serial.println();
    Serial.println("Node Role:");
    Serial.println("CHILD");
    Serial.println();
    Serial.println("Child ID:");
    Serial.println("LOCAL_ONLY");
    Serial.println();
    Serial.println("BLE:");
    Serial.println("READY");
    Serial.println();
    Serial.println("State:");
    Serial.println("NORMAL");
    Serial.println();
    Serial.println("=================================");
    Serial.println();
    Serial.printf("[INFO] BOOT button configured on GPIO %d (hold 2s for SOS)\n", PIN_BOOT_BUTTON);
    Serial.printf("[INFO] Status RGB LED configured on GPIO %d\n", PIN_RGB_LED);
    Serial.printf("[INFO] Ephemeral ID: %s (Privacy active)\n", SafeBandNodeIdentity::getEphemeralId().c_str());
    Serial.println();
}

void setup() {
    // Initialize Serial diagnostics
    Serial.begin(115200);
    delay(200);

    // Initialize local node identity and profile
    SafeBandNodeIdentity::init("SB-CHLD");

    // Initialize onboard RGB status LED
    StatusLed::init();

    // Print boot diagnostic banner
    printBootBanner();

    // Initialize SOS button controller
    SosController::init(onSosStateChanged);

    // Initialize BLE subsystem and start advertising
    SafeBandBleManager::init();

    // Set state to NORMAL safe state
    SafeBandNodeIdentity::setState(NODE_NORMAL);
}

static uint32_t lastHeartbeatMs = 0;

void loop() {
    // 1. Process SOS button state machine & debouncing
    SosController::update();

    // 2. Update status LED animation and state
    StatusLed::update(SafeBandNodeIdentity::getState());

    // 3. Process BLE loop events (ephemeral rotation, reconnection)
    SafeBandBleManager::handleLoop();

    // 4. Periodic diagnostic heartbeat (every 30 seconds)
    uint32_t now = millis();
    if (now - lastHeartbeatMs >= 30000UL) {
        lastHeartbeatMs = now;
        Serial.printf("[HEARTBEAT] Uptime: %lu s | State: %s | EphemeralID: %s | BLE: %s\n",
            (unsigned long)(now / 1000UL),
            SafeBandNodeIdentity::getStateName(SafeBandNodeIdentity::getState()),
            SafeBandNodeIdentity::getEphemeralId(now).c_str(),
            SafeBandBleManager::isDeviceConnected() ? "CONNECTED" : "ADVERTISING"
        );
    }

    // Small yield to satisfy watchdog and power efficiency
    delay(10);
}
