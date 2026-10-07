#pragma once

#include <Arduino.h>

// =============================================================================
// SafeBand Guardian - ESP32-S3 Physical Child Node Configuration
// =============================================================================

// Board & Pin Configuration
// ESP32-S3 DevKit has BOOT button on GPIO 0 (active LOW with pull-up)
#define PIN_BOOT_BUTTON             0

// Onboard WS2812 addressable RGB LED
// Most ESP32-S3 dev boards use GPIO 48 or GPIO 38
#ifndef RGB_BUILTIN
#define RGB_BUILTIN                 48
#endif
#define PIN_RGB_LED                 RGB_BUILTIN
#define PIN_RGB_LED_ALT             38

// SafeBand Protocol Constants (matching Android BleSafetyManager.kt)
#define SAFEBAND_MANUFACTURER_ID    0x5AFE
#define SAFEBAND_MAGIC_BYTE_1       0x53    // 'S'
#define SAFEBAND_MAGIC_BYTE_2       0x42    // 'B'

// BLE Service & Characteristic UUIDs
#define SAFEBAND_SERVICE_UUID       "00005afe-0000-1000-8000-00805f9b34fb"
#define SAFEBAND_CHAR_STATUS_UUID   "00005afe-0001-1000-8000-00805f9b34fb"

// Public BLE Device Name (privacy-preserving, no child name)
#define SAFEBAND_BLE_DEVICE_NAME    "SafeBand-Child"

// Node & Firmware Metadata
#define SAFEBAND_FIRMWARE_VERSION   "1.0.0"
#define SAFEBAND_PROTOCOL_VERSION   2
#define SAFEBAND_HARDWARE_NODE_TYPE "PHYSICAL_CHILD_NODE"

// SOS Button Timing (Phase 7)
#define SOS_HOLD_DURATION_MS        2000    // 2.0s press-and-hold to trigger SOS
#define SOS_CLEAR_HOLD_DURATION_MS  2000    // 2.0s press-and-hold to cancel/clear SOS
#define BUTTON_DEBOUNCE_MS          50      // 50ms switch debounce filter

// Ephemeral ID Rotation (Phase 4: 15-minute window = 900,000 ms)
#define EPHEMERAL_WINDOW_MS         900000UL

// BLE Advertising Interval (in 0.625ms units: 160 = 100ms for fast discovery)
#define BLE_ADV_INTERVAL_MIN        160
#define BLE_ADV_INTERVAL_MAX        320
