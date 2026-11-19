# SafeBand Guardian v4.0.0 — BLE 0x5AFE Protocol Specification

## 1. Overview

The SafeBand Guardian BLE protocol utilizes custom Bluetooth Low Energy advertisements and GATT services to provide robust, power-efficient, and cryptographically verified telemetry between the ESP32-S3 wearable node and nearby Android devices.

* **Manufacturer Company Identifier**: `0x5AFE`
* **Primary Service UUID**: `00005AFE-0000-1000-8000-00805F9B34FB`

---

## 2. Advertisement Packet Structure (0x5AFE)

The wearable broadcasts a 26-byte manufacturer data advertisement under ID `0x5AFE`:

```
+------------+-----------+-----------+------------+------------+-----------+------------+-------------+
| Byte 0..1  | Byte 2    | Byte 3    | Byte 4..5  | Byte 6..9  | Byte 10   | Byte 11    | Byte 12..25 |
| Company ID | Protocol  | Alert     | Battery /  | Device ID  | Sequence  | Reserved / | AES-128-GCM |
|  (0x5AFE)  |  Version  | Type Flag | Status     | Short Hash | Counter   | Tamper Pin | Tag/Payload |
+------------+-----------+-----------+------------+------------+-----------+------------+-------------+
```

### 2.1. Byte Field Breakdown

| Offset | Length | Name | Description / Valid Values |
| :--- | :--- | :--- | :--- |
| `0..1` | 2 | Company ID | `0xFE, 0x5A` (Little-endian for `0x5AFE`) |
| `2` | 1 | Protocol Version | `0x04` (v4.0.0 specification) |
| `3` | 1 | Alert Type Flag | `0x00`: Normal Heartbeat<br>`0x01`: Low Battery<br>`0x02`: SOS Manual Button Panic<br>`0x03`: Hardware Strap Tamper Cut (GPIO 3/4)<br>`0x04`: Maintenance Mode Active |
| `4` | 1 | Battery Percentage | `0..100` (%) |
| `5` | 1 | Hardware Status Flags | Bit 0: Strap Circuit Continuous (1=Intact, 0=Broken)<br>Bit 1: Maintenance Mode (1=Active, 0=Disarmed)<br>Bit 2: Button Depressed<br>Bits 3..7: Reserved |
| `6..9` | 4 | Device ID Short Hash | 4-byte truncated SHA-256 of Wearable MAC / Hardware UUID |
| `10` | 1 | Sequence Counter | Monotonically increasing `0..255` counter for replay prevention |
| `11` | 1 | Tamper Pin Code | `0x34` (Verifies dynamic loop test between GPIO 3 and GPIO 4) |
| `12..25` | 14 | Encrypted Payload | AES-128-GCM authentication tag and encrypted telemetry |

---

## 3. GATT Services and Characteristics

When a parent device establishes a secure BLE connection to the wearable, the following GATT service hierarchy is exposed:

### Primary Service: `00005AFE-0000-1000-8000-00805F9B34FB`

| Characteristic UUID | Property | Access | Description |
| :--- | :--- | :--- | :--- |
| `...-5AFE-0001` | Read, Write | Authenticated | **Device Configuration**: Read device metadata; write parent association and child name. |
| `...-5AFE-0002` | Write | Authenticated | **Pairing Challenge**: Handshake exchange for AES key provisioning. |
| `...-5AFE-0003` | Read, Notify | Open | **Live Telemetry**: Stream real-time battery, strap status, and button state. |
| `...-5AFE-0004` | Write | Authenticated | **Maintenance Control**: Enable/disable 5-minute tamper maintenance window. |
| `...-5AFE-0005` | Write | Authenticated | **Emergency Acknowledge**: Reset local SOS alarm buzzer/LED after parent confirms incident. |

---

## 4. Tamper Detection Subsystem (GPIO 3 / GPIO 4)

1. **Hardware Loop**: GPIO 3 is configured as an output pin; GPIO 4 is configured as an input pin with internal pull-down resistor enabled (~45kΩ).
2. **Dynamic Continuity Pulse**:
   * ESP32 firmware holds GPIO 3 LOW.
   * Every 500ms, firmware drives GPIO 3 HIGH.
   * A settling delay of **200µs** (`delayMicroseconds(200)`) allows line capacitance to charge through strap ribbon traces.
   * Firmware samples GPIO 4:
     * If GPIO 4 is HIGH: Circuit is intact (`STRAP_CLOSED`).
     * If GPIO 4 is LOW: Circuit has been severed or cut (`STRAP_OPEN`).
   * Firmware pulls GPIO 3 LOW again.
3. **Tamper Trip Behavior**:
   * If two consecutive sample cycles detect `STRAP_OPEN`, firmware immediately sets `is_tamper_tripped = true`.
   * BLE advertising interval accelerates from 1000ms to 200ms with Alert Type Flag `0x03` (`TAMPER_DETECTED`).
