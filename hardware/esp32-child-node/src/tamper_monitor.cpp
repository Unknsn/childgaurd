#include "tamper_monitor.h"
#include "node_config.h"

TamperState TamperMonitor::s_currentState = TAMPER_INITIALIZING;
TamperStateCallback TamperMonitor::s_callback = nullptr;
uint32_t TamperMonitor::s_lastCheckMs = 0;
uint8_t TamperMonitor::s_consecutiveFailures = 0;
uint8_t TamperMonitor::s_consecutiveSuccesses = 0;
uint32_t TamperMonitor::s_maintenanceExpiryMs = 0;
uint32_t TamperMonitor::s_lastMaintenanceSeq = 0;

void TamperMonitor::init(TamperStateCallback callback) {
    s_callback = callback;
    s_currentState = TAMPER_INITIALIZING;
    s_consecutiveFailures = 0;
    s_consecutiveSuccesses = 0;
    s_maintenanceExpiryMs = 0;

    Serial.println("[TAMPER] Initializing GPIO3 (TX) and GPIO4 (RX) continuity loop...");

    pinMode(PIN_TAMPER_TX, OUTPUT);
    digitalWrite(PIN_TAMPER_TX, LOW);

    pinMode(PIN_TAMPER_RX, INPUT_PULLDOWN);

    delay(20);

    bool isFault = false;
    bool initialIntact = performContinuityTest(isFault);

    if (isFault) {
        Serial.println("[TAMPER] Boot Self-Test: Sensor electrical fault detected!");
        transitionState(TAMPER_SENSOR_FAULT);
    } else if (initialIntact) {
        Serial.println("[TAMPER] Boot Self-Test: Continuity verified. Strap is PROTECTED.");
        transitionState(TAMPER_PROTECTED);
    } else {
        Serial.println("[TAMPER] Boot Self-Test: Strap open at boot. Defaulting to PROTECTED with monitoring.");
        // During initial boot or before band is put on, start in PROTECTED or SUSPECTED
        transitionState(TAMPER_PROTECTED);
    }
}

bool TamperMonitor::performContinuityTest(bool& isFault) {
    isFault = false;

    // Phase 1: Dynamic TX HIGH test (200us pulse to allow physical strap capacitance to settle)
    digitalWrite(PIN_TAMPER_TX, HIGH);
    delayMicroseconds(200);
    int readHigh = digitalRead(PIN_TAMPER_RX);

    // Phase 2: Dynamic TX LOW test (200us pulse to allow pulldown discharge)
    digitalWrite(PIN_TAMPER_TX, LOW);
    delayMicroseconds(200);
    int readLow = digitalRead(PIN_TAMPER_RX);

    // Dynamic continuity check:
    // If intact, RX mirrors TX (HIGH when HIGH, LOW when LOW)
    if (readHigh == HIGH && readLow == LOW) {
        return true; // Intact and verified
    }

    // If both read HIGH: Short-circuit to 3.3V power rail or external injection attempt!
    if (readHigh == HIGH && readLow == HIGH) {
        isFault = true;
        return false;
    }

    // If readHigh == LOW and readLow == HIGH: Inverted logic / hardware noise
    if (readHigh == LOW && readLow == HIGH) {
        isFault = true;
        return false;
    }

    // Both read LOW: Open circuit (strap detached or severed)
    return false;
}

void TamperMonitor::transitionState(TamperState newState) {
    if (s_currentState == newState) return;

    TamperState oldState = s_currentState;
    s_currentState = newState;

    Serial.printf("[TAMPER] State Transition: %s -> %s\n",
        getStateName(oldState), getStateName(newState));

    if (s_callback) {
        s_callback(oldState, newState);
    }
}

void TamperMonitor::update() {
    uint32_t now = millis();

    // Check interval: every 200ms
    if (now - s_lastCheckMs < TAMPER_CHECK_INTERVAL_MS) {
        return;
    }
    s_lastCheckMs = now;

    // Check authorized maintenance expiration
    if (s_currentState == TAMPER_AUTHORIZED_DISCONNECTION) {
        if (now >= s_maintenanceExpiryMs) {
            Serial.println("[TAMPER] Authorized maintenance window expired! Evaluating strap state...");
            s_maintenanceExpiryMs = 0;
            NodeConfig::setTamperAuthorizedUntil(0);

            bool isFault = false;
            bool intact = performContinuityTest(isFault);
            if (!intact) {
                Serial.println("[TAMPER] Maintenance expired and strap is NOT connected! Escalating to TAMPER_CONFIRMED!");
                transitionState(TAMPER_CONFIRMED);
                return;
            } else {
                Serial.println("[TAMPER] Maintenance expired and strap is intact. Restored to PROTECTED.");
                transitionState(TAMPER_PROTECTED);
                return;
            }
        }
    }

    bool isFault = false;
    bool intact = performContinuityTest(isFault);

    if (isFault) {
        s_consecutiveFailures++;
        if (s_consecutiveFailures >= TAMPER_DEBOUNCE_CYCLES) {
            transitionState(TAMPER_SENSOR_FAULT);
        }
        return;
    }

    if (intact) {
        // Continuity is verified
        s_consecutiveFailures = 0;
        s_consecutiveSuccesses++;

        if (s_currentState == TAMPER_SUSPECTED) {
            // Transient glitch / debounce cleared
            transitionState(TAMPER_PROTECTED);
        } else if (s_currentState == TAMPER_CONFIRMED || s_currentState == TAMPER_AUTHORIZED_DISCONNECTION) {
            if (s_consecutiveSuccesses >= TAMPER_RESTORE_CYCLES) {
                if (s_currentState == TAMPER_AUTHORIZED_DISCONNECTION) {
                    Serial.println("[TAMPER] Strap reconnected during maintenance window. Restoring protection.");
                    s_maintenanceExpiryMs = 0;
                    NodeConfig::setTamperAuthorizedUntil(0);
                    transitionState(TAMPER_PROTECTED);
                } else {
                    transitionState(TAMPER_RESTORING);
                    delay(50);
                    transitionState(TAMPER_PROTECTED);
                }
            }
        } else if (s_currentState == TAMPER_RESTORING) {
            transitionState(TAMPER_PROTECTED);
        } else if (s_currentState == TAMPER_SENSOR_FAULT) {
            if (s_consecutiveSuccesses >= TAMPER_RESTORE_CYCLES) {
                transitionState(TAMPER_PROTECTED);
            }
        }
    } else {
        // Continuity failed (open circuit)
        s_consecutiveSuccesses = 0;
        s_consecutiveFailures++;

        if (s_currentState == TAMPER_AUTHORIZED_DISCONNECTION) {
            // Disconnection authorized by guardian: do not raise alarm
            return;
        }

        if (s_currentState == TAMPER_PROTECTED) {
            transitionState(TAMPER_SUSPECTED);
        } else if (s_currentState == TAMPER_SUSPECTED) {
            if (s_consecutiveFailures >= TAMPER_DEBOUNCE_CYCLES) {
                Serial.printf("[TAMPER] ALERT: Continuity broken for %d consecutive cycles! TAMPER CONFIRMED!\n",
                    s_consecutiveFailures);
                transitionState(TAMPER_CONFIRMED);
            }
        }
    }
}

TamperState TamperMonitor::getState() {
    return s_currentState;
}

bool TamperMonitor::isTamperTriggered() {
    return (s_currentState == TAMPER_CONFIRMED);
}

const char* TamperMonitor::getStateName(TamperState state) {
    switch (state) {
        case TAMPER_INITIALIZING: return "INITIALIZING";
        case TAMPER_PROTECTED: return "PROTECTED";
        case TAMPER_SUSPECTED: return "SUSPECTED_TAMPER";
        case TAMPER_CONFIRMED: return "TAMPER_CONFIRMED";
        case TAMPER_AUTHORIZED_DISCONNECTION: return "AUTHORIZED_DISCONNECTION";
        case TAMPER_RESTORING: return "RESTORING";
        case TAMPER_SENSOR_FAULT: return "SENSOR_FAULT";
        default: return "UNKNOWN";
    }
}

bool TamperMonitor::authorizeMaintenance(uint32_t durationSeconds, const String& token, uint32_t seq) {
    if (!NodeConfig::verifyAuthToken(token)) {
        Serial.println("[TAMPER] ERR: Unauthorized maintenance attempt! Invalid token.");
        return false;
    }

    if (seq > 0) {
        if (seq <= s_lastMaintenanceSeq) {
            Serial.printf("[TAMPER] ERR: Replay attack detected! Maintenance seq %u <= last seq %u\n",
                seq, s_lastMaintenanceSeq);
            return false;
        }
        s_lastMaintenanceSeq = seq;
    }

    if (durationSeconds < 10) durationSeconds = DEFAULT_MAINTENANCE_SEC;
    if (durationSeconds > MAX_MAINTENANCE_SEC) durationSeconds = MAX_MAINTENANCE_SEC;

    s_maintenanceExpiryMs = millis() + (durationSeconds * 1000UL);
    NodeConfig::setTamperAuthorizedUntil(s_maintenanceExpiryMs);

    Serial.printf("[TAMPER] Guardian authorized maintenance granted for %u seconds (Seq: %u).\n", durationSeconds, seq);
    transitionState(TAMPER_AUTHORIZED_DISCONNECTION);
    return true;
}

void TamperMonitor::cancelMaintenance() {
    Serial.println("[TAMPER] Maintenance explicitly cancelled by Guardian.");
    s_maintenanceExpiryMs = 0;
    NodeConfig::setTamperAuthorizedUntil(0);
    s_consecutiveFailures = 0;
    s_consecutiveSuccesses = 0;

    bool isFault = false;
    bool intact = performContinuityTest(isFault);
    if (intact) {
        transitionState(TAMPER_PROTECTED);
    } else {
        transitionState(TAMPER_CONFIRMED);
    }
}

uint32_t TamperMonitor::getRemainingMaintenanceSeconds() {
    if (s_currentState != TAMPER_AUTHORIZED_DISCONNECTION) return 0;
    uint32_t now = millis();
    if (now >= s_maintenanceExpiryMs) return 0;
    return (s_maintenanceExpiryMs - now) / 1000UL;
}

void TamperMonitor::clearTamper() {
    s_consecutiveFailures = 0;
    s_consecutiveSuccesses = 0;
    bool isFault = false;
    if (performContinuityTest(isFault)) {
        transitionState(TAMPER_PROTECTED);
    }
}
