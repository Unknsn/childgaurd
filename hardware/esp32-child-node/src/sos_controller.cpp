#include "sos_controller.h"

SosStateCallback SosController::s_callback = nullptr;
bool SosController::s_lastButtonRaw = HIGH;
uint32_t SosController::s_pressStartMs = 0;
bool SosController::s_isPressed = false;
bool SosController::s_sosConfirmedThisPress = false;

void SosController::init(SosStateCallback callback) {
    s_callback = callback;
    pinMode(PIN_BOOT_BUTTON, INPUT_PULLUP);
    s_lastButtonRaw = digitalRead(PIN_BOOT_BUTTON);
    s_pressStartMs = 0;
    s_isPressed = false;
    s_sosConfirmedThisPress = false;
}

bool SosController::isSosActive() {
    return SafeBandNodeIdentity::getState() == NODE_SOS_ACTIVE;
}

void SosController::clearSos() {
    NodeState oldState = SafeBandNodeIdentity::getState();
    SafeBandNodeIdentity::setState(NODE_NORMAL);
    Serial.println("[SOS] SOS cleared -> Returning to NORMAL safe state");
    if (s_callback) {
        s_callback(oldState, NODE_NORMAL);
    }
}

void SosController::update() {
    uint32_t now = millis();
    // BOOT button is active LOW (pressed = LOW, released = HIGH)
    bool rawPin = digitalRead(PIN_BOOT_BUTTON);
    bool isDown = (rawPin == LOW);

    NodeState currentState = SafeBandNodeIdentity::getState();

    if (isDown) {
        if (!s_isPressed) {
            // First detection of button press
            s_isPressed = true;
            s_pressStartMs = now;
            s_sosConfirmedThisPress = false;
            Serial.println("[SOS] Button press detected - starting 2s countdown...");
        } else {
            uint32_t holdDuration = now - s_pressStartMs;

            // Debounce check
            if (holdDuration >= BUTTON_DEBOUNCE_MS) {
                if (currentState == NODE_NORMAL && !s_sosConfirmedThisPress) {
                    // Update state to countdown
                    SafeBandNodeIdentity::setState(NODE_SOS_COUNTDOWN);
                }

                // Check if held for the full 2.0 seconds
                if (holdDuration >= SOS_HOLD_DURATION_MS && !s_sosConfirmedThisPress) {
                    s_sosConfirmedThisPress = true;

                    if (currentState == NODE_SOS_COUNTDOWN || currentState == NODE_NORMAL) {
                        // Confirm SOS!
                        Serial.println("=========================================");
                        Serial.println("[SOS] >>> SOS CONFIRMED (2s hold) <<<");
                        Serial.println("[SOS] Broadcasting CRITICAL emergency event!");
                        Serial.println("=========================================");
                        SafeBandNodeIdentity::setState(NODE_SOS_ACTIVE);
                        if (s_callback) {
                            s_callback(currentState, NODE_SOS_ACTIVE);
                        }
                    } else if (currentState == NODE_SOS_ACTIVE) {
                        // Long press while already active clears SOS
                        Serial.println("=========================================");
                        Serial.println("[SOS] >>> SOS CANCELLED BY USER (2s hold) <<<");
                        Serial.println("[SOS] Returning to NORMAL safe state");
                        Serial.println("=========================================");
                        SafeBandNodeIdentity::setState(NODE_NORMAL);
                        if (s_callback) {
                            s_callback(NODE_SOS_ACTIVE, NODE_NORMAL);
                        }
                    }
                }
            }
        }
    } else {
        // Button is released
        if (s_isPressed) {
            uint32_t holdDuration = now - s_pressStartMs;
            s_isPressed = false;

            if (currentState == NODE_SOS_COUNTDOWN) {
                // Released before 2-second hold completed -> cancel countdown
                Serial.printf("[SOS] Button released after %lu ms (short press) - SOS cancelled.\n", (unsigned long)holdDuration);
                SafeBandNodeIdentity::setState(NODE_NORMAL);
                if (s_callback) {
                    s_callback(NODE_SOS_COUNTDOWN, NODE_NORMAL);
                }
            }
        }
    }

    s_lastButtonRaw = rawPin;
}
