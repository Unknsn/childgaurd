# SafeBand Guardian: Hardware Integration & Wearable Specification

## 1. Physical Node Specification (ESP32-S3)

The SafeBand child wearable is powered by an **Espressif ESP32-S3** microcontroller (dual-core Xtensa LX7 @ 240 MHz, integrated 2.4 GHz Wi-Fi and Bluetooth 5.0 LE).

```
                      ┌──────────────────────────────────────┐
                      │          ESP32-S3 Node               │
                      │                                      │
  [GPIO 0: BOOT BTN] ──► Push-Button (2s Hold SOS Trigger)   │
                      │                                      │
                      │  WS2812 RGB LED ──► Status Pulse     │ [GPIO 48]
                      │                                      │
                      │  BLE 5.0 Radio ──► 0x5AFE Broadcast  │
                      └──────────────────────────────────────┘
```

### Key Hardware Specifications:
- **Processor:** ESP32-S3FH4R2 / ESP32-S3-WROOM-1
- **BLE Subsystem:** Bluetooth Low Energy 5.0 with Coded PHY long-range support
- **Power Supply:** 3.7V 350mAh LiPo battery with MCP73831 charging IC
- **Sensors:** MPU6050 / LSM6DSO 6-axis IMU (I2C) for fall and tampering detection
- **Feedback Indicators:** Onboard WS2812 addressable RGB LED (GPIO 48)

---

## 2. GPIO Pinout & Peripherals

| Peripheral | GPIO Pin | Configuration | Hardware Role |
| :--- | :--- | :--- | :--- |
| **Emergency SOS Button** | `GPIO 0` | Input, Internal Pull-Up, Active Low | 2-second press triggers immediate critical emergency broadcast. |
| **WS2812 RGB Status LED** | `GPIO 48` | Output, RMT Channel | Visual feedback for device state (Armed, Siren, Handshake). |
| **I2C SDA (Motion Sensor)** | `GPIO 8` | Bi-Directional, Open-Drain | Accelerometer / Gyroscope communication. |
| **I2C SCL (Motion Sensor)** | `GPIO 9` | Output, Open-Drain | I2C Clock line. |
| **Battery ADC Sense** | `GPIO 1` | Analog Input (1:2 Voltage Divider) | Real-time LiPo cell voltage monitoring. |

---

## 3. WS2812 LED Status Indicators

The onboard RGB Neopixel provides intuitive visual status:

| LED Color & Animation | State Name | Meaning |
| :--- | :--- | :--- |
| 🟢 **Slow Green Pulse (1 Hz)** | `ARMED_STANDBY` | Device armed, sensors active, nominal battery level. |
| 🔴 **Fast Red Flashing (10 Hz)** | `EMERGENCY_SIREN` | Critical SOS active, high-frequency BLE sirens transmitting. |
| 🔵 **Single Cyan Flash** | `PAIRING_HANDSHAKE` | Successful pairing challenge acknowledgment from mobile app. |
| 🟡 **Steady Yellow** | `LOW_BATTERY` | Battery level below 15%; sensor sampling rate reduced. |
| ⚫ **Off / Blank** | `DEEP_SLEEP` | Sleep state; wakes immediately on GPIO 0 interrupt. |

---

## 4. BLE Communication Contract (`0x5AFE`)

The mobile Android application and ESP32 firmware communicate using non-connectable BLE manufacturer advertisements under Company ID `0x5AFE`.

> **Byte Ordering Note:** In Bluetooth LE advertising packets, company identifiers are transmitted in little-endian order. The raw packet header begins with `0xFE, 0x5A`.

### 4.1 Packet Type 0x01: Core Telemetry (24 Bytes Total)
Transmitted periodically in standby (1 Hz) and rapidly during emergencies (10 Hz):
```
+---------+---------+----------+-----------+--------------------+-------------------+-------------------+-------------+
| Byte 0  | Byte 1  | Byte 2-8 |  Byte 9   |    Bytes 10-13     |    Bytes 14-17    |    Bytes 18-21    | Bytes 22-23 |
|  0x53   |  0x42   | Node ID  | Risk Byte |   Timestamp (s)    |   Latitude (f)    |   Longitude (f)   |   Batt/Stat |
|  ('S')  |  ('B')  |  7B ASCII| (0,1,2,3) | 32-bit Int (epoch) | IEEE 754 Float32  | IEEE 754 Float32  |  2-byte flags
+---------+---------+----------+-----------+--------------------+-------------------+-------------------+-------------+
```
- **Magic Bytes:** `0x53, 0x42` (ASCII `'S'`, `'B'`).
- **Node Identifier:** 7 ASCII characters identifying the band (e.g. `EP-C0D7`).
- **Risk Byte:**
  - `0x00`: Normal / Armed
  - `0x01`: Low (Minor Anomaly)
  - `0x02`: Medium (Warning / Countdown Active)
  - `0x03`: High / Emergency SOS Active

### 4.2 Packet Type 0x02: Child Bio Core
Interleaved during emergency broadcasts:
```
+---------+---------+----------+-----------+-----------------------+----------+-------------+
| Byte 0  | Byte 1  | Byte 2-8 |  Byte 9   |      Bytes 10-21      | Byte 22  | Bytes 23-25 |
|  0x53   |  0x42   | Node ID  | Type 0x02 |    Child Name (12B)   | Age (1B) | Blood (3B)  |
+---------+---------+----------+-----------+-----------------------+----------+-------------+
```

### 4.3 Packet Type 0x03: Primary Guardian Phone
Allows bystander phone 1-tap call assistance:
```
+---------+---------+----------+-----------+------------------------------------+
| Byte 0  | Byte 1  | Byte 2-8 |  Byte 9   |            Bytes 10-25             |
|  0x53   |  0x42   | Node ID  | Type 0x03 | Primary Guardian Phone ASCII (16B) |
+---------+---------+----------+-----------+------------------------------------+
```

---

## 5. Firmware Build & Flashing Guide

ESP-IDF v5.1+ source code is located in `hardware/esp32_child_node/`.

### Prerequisites
- ESP-IDF v5.1 or ESP-IDF Extension for VS Code / PlatformIO
- USB-C cable connected to ESP32-S3 USB-OTG port

### Build and Flash Commands
```bash
cd hardware/esp32_child_node

# Set target microcontroller
idf.py set-target esp32s3

# Configure project settings (optional)
idf.py menuconfig

# Build, flash to COM port, and start serial monitor
idf.py build flash monitor
```

---

## 6. Honest Telemetry & Sensor Uncertainty

In accordance with Core Principle 3 (**No Fake Functionality**):
- If the ESP32 node is disconnected or out of range, the mobile UI explicitly reports:
  - **Connection State:** `DISCONNECTED` or `STALE`
  - **Battery Percentage:** Displayed as `Unavailable` rather than inventing 100% or last-known values.
  - **Signal Strength:** Shown as `-- dBm` with clear timestamp of last successful packet reception.
