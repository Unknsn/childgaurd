#include "status_led.h"

uint32_t StatusLed::s_bleFlashUntilMs = 0;

void StatusLed::init() {
    // Write neutral white on boot
    writeRgb(40, 40, 40);
}

void StatusLed::writeRgb(uint8_t r, uint8_t g, uint8_t b) {
    // Drive GPIO 48 (standard ESP32-S3 DevKit WS2812 pin)
    neopixelWrite(48, r, g, b);
    // Drive GPIO 38 (alternate ESP32-S3 WS2812 pin)
    neopixelWrite(38, r, g, b);
    // Drive RGB_BUILTIN (built-in core macro mapping)
    #ifdef RGB_BUILTIN
    neopixelWrite(RGB_BUILTIN, r, g, b);
    #endif
}

void StatusLed::setRawColor(uint8_t r, uint8_t g, uint8_t b) {
    writeRgb(r, g, b);
}

void StatusLed::flashBleConnected() {
    s_bleFlashUntilMs = millis() + 500; // 500ms cyan confirmation flash
}

void StatusLed::update(NodeState state) {
    uint32_t now = millis();

    // Handle brief BLE connection confirmation flash
    if (now < s_bleFlashUntilMs) {
        writeRgb(0, 180, 255); // Cyan flash
        return;
    }

    switch (state) {
        case NODE_INITIALIZING:
            // White / neutral startup indication
            writeRgb(40, 40, 40);
            break;

        case NODE_NORMAL:
            // Steady green safe state
            writeRgb(0, 50, 0);
            break;

        case NODE_SOS_COUNTDOWN: {
            // Pulsing yellow/orange during 2-second hold countdown
            uint32_t phase = (now % 400); // 400ms cycle
            float factor = (phase < 200) ? (phase / 200.0f) : ((400 - phase) / 200.0f);
            uint8_t r = (uint8_t)(255 * factor);
            uint8_t g = (uint8_t)(140 * factor);
            writeRgb(r, g, 0);
            break;
        }

        case NODE_SOS_ACTIVE:
            // Vivid urgent red
            writeRgb(255, 0, 0);
            break;

        case NODE_ERROR: {
            // Rapid red warning blink (200ms period)
            bool on = (now % 200) < 100;
            writeRgb(on ? 255 : 0, 0, 0);
            break;
        }
    }
}
