# SafeBand Guardian — ESP32-S3 Physical Child Node (V1 Prototype)

> **PROTOTYPE CHILD NODE NOTICE**  
> This firmware implements the BLE-only physical child node proof of concept for the SafeBand Guardian system.  
> It operates with **zero cloud dependencies** and **zero Wi-Fi connectivity**.

---

## 1. Board Identification & Specifications

- **Microcontroller**: Espressif ESP32-S3 (QFN56 revision v0.2)
- **CPU**: Dual-Core Xtensa LX7 @ 240 MHz + Low-Power Core
- **PSRAM**: 8 MB Embedded PSRAM (AP_3v3)
- **Flash Memory**: 16 MB Quad SPI Flash (3.3V)
- **Wireless**: Bluetooth 5.0 Low Energy (BLE)
- **Interfaces**:
  - `COM11`: CH343 USB-to-UART Bridge (programming / serial monitor)
  - `COM8`: Native USB-JTAG/Serial interface
- **Physical Controls**:
  - `RESET` button: Hardware system restart
  - `BOOT` button: GPIO 0 (active LOW with internal pull-up) — **Prototype SOS Trigger**
- **Optical Feedback**:
  - Onboard WS2812 addressable RGB LED (`GPIO 48` / `GPIO 38`)

---

## 2. Hardware Assumptions & Baseline Constraints

1. **Physical Inputs Permitted**: Only `BOOT` (GPIO 0) and `RESET` (EN).
2. **Sensors**: **No external sensors connected**.
   - No GPS module -> Telemetry coordinates are reported as `UNKNOWN / No GPS Fix` (0.0f).
   - No IMU -> No physical motion/fall data.
   - No Battery gauge -> Battery level is reported as `NOT_AVAILABLE`.
3. **No External Circuitry**: No external buzzers, motors, buttons, or wiring.
4. **No Wi-Fi / Cellular**: Pure BLE peripheral and advertiser.

---

## 3. BLE Architecture & Protocol Format

The ESP32-S3 child node strictly implements the canonical SafeBand V2 BLE protocol specification shared identically with Android:

### Service & Manufacturer Metadata
- **Manufacturer Company ID**: `0x5AFE` (23294 decimal, encoded Little-Endian `[0xFE, 0x5A]`)
- **Service UUID**: `00005afe-0000-1000-8000-00805f9b34fb` (128-bit)
- **Telemetry Characteristic UUID**: `00005afe-0001-1000-8000-00805f9b34fb` (READ, NOTIFY)
- **Public Device Name**: `SafeBand` (advertised in Scan Response)

### Shared Protocol Byte-by-Byte Table

| Packet Byte Offset | Length | Type | Description | ESP32 C++ Encoder | Android Kotlin Decoder (`BleSafetyManager`) | Wire Value |
|---|---|---|---|---|---|---|
| `[0]` | 1 | uint8_t | Magic Byte 1 | `MAGIC_BYTE_1` | `payload[0] == 0x53.toByte()` | `0x53` ('S') |
| `[1]` | 1 | uint8_t | Magic Byte 2 | `MAGIC_BYTE_2` | `payload[1] == 0x42.toByte()` | `0x42` ('B') |
| `[2..8]` | 7 | char[7] | Ephemeral ID | `memcpy(&p[2], ephem.c_str(), 7)` | `String(payload.copyOfRange(2, 9), UTF_8)` | `EP-XXXX` |
| `[9]` | 1 | uint8_t | Risk Level Code | `p[9] = (uint8_t)currentRisk` | `when (payload[9].toInt()) { 0->NORMAL, 4->CRITICAL }` | `0` = NORMAL, `4` = CRITICAL |
| `[10..13]` | 4 | uint32_t | Timestamp (BE) | `htonl(now)` | `ByteBuffer.wrap(payload, 10, 4).int.toLong()` | Unix epoch / boot seconds |
| `[14..17]` | 4 | float | Latitude (BE) | `0.0f` (No GPS) | `ByteBuffer.wrap(payload, 14, 4).float` | `0.0f` -> `null` (No GPS) |
| `[18..21]` | 4 | float | Longitude (BE) | `0.0f` (No GPS) | `ByteBuffer.wrap(payload, 18, 4).float` | `0.0f` -> `null` (No GPS) |

### Over-The-Air Packet Budget (Strict BLE 31-Byte Limit)

Bluetooth Core Specification strictly enforces a maximum payload of **31 bytes** for legacy advertising PDUs (`ADV_IND`). Transmitting UUID + Manufacturer Data in the primary packet causes packet overflow (47 bytes) which ESP-IDF rejects (`ESP_ERR_INVALID_ARG`). SafeBand V2 partitions the broadcast as follows:

#### Primary Advertising Packet (`advData`) — Exactly 29 Bytes (<= 31 Bytes)
1. **Flags AD Structure (3 Bytes)**:
   - `[0]`: `0x02` (Length = 2 bytes)
   - `[1]`: `0x01` (AD Type: Flags)
   - `[2]`: `0x06` (`LE General Discoverable` + `BR/EDR Not Supported`)
2. **Manufacturer Specific Data AD Structure (26 Bytes)**:
   - `[3]`: `0x19` (Length = 25 bytes: 2B Company ID + 22B Payload)
   - `[4]`: `0xFF` (AD Type: Manufacturer Specific Data)
   - `[5..6]`: `0xFE, 0x5A` (Company ID `0x5AFE` in Little-Endian)
   - `[7..28]`: 22-byte SafeBand Telemetry Payload (Magic 'SB' + Ephemeral ID + Risk + Telemetry)

#### Scan Response Packet (`scanResponseData`) — Exactly 28 Bytes (<= 31 Bytes)
1. **128-bit Complete Service UUID (18 Bytes)**:
   - `[0]`: `0x11` (Length = 17 bytes: 1B Type + 16B UUID)
   - `[1]`: `0x07` (AD Type: Complete List of 128-bit Service Class UUIDs)
   - `[2..17]`: `00005afe-0000-1000-8000-00805f9b34fb` (Little-Endian on-wire)
2. **Shortened Local Name (10 Bytes)**:
   - `[18]`: `0x09` (Length = 9 bytes: 1B Type + 8B Name)
   - `[19]`: `0x08` (AD Type: Shortened Local Name)
   - `[20..27]`: `"SafeBand"` ASCII

---

## 4. Privacy Boundary & Ephemeral ID Mechanism

In accordance with SafeBand V2 privacy principles:
- **Child's real name, age, blood type, and emergency contacts are NEVER transmitted over BLE.**
- All sensitive profile fields (`CHILD-001`, `Demo Child`, etc.) are retained **locally on-device**.
- The public BLE advertisement broadcasts only a rotating **ephemeral identifier** (`EP-XXXX`).
- Ephemeral identifiers are deterministically derived via `hash(deviceId + "-" + epochWindow)` every **15 minutes** (900,000 ms), completely preventing third-party tracking.

---

## 5. Physical Controls & SOS Behavior

### BOOT Button -> SOS Trigger
- **Normal State**: Node state is `NORMAL`. Status LED is steady **Green**.
- **Press & Hold (>= 2 Seconds)**:
  1. Instant button press detected -> enters `SOS_COUNTDOWN`.
  2. RGB LED pulses **Yellow / Orange**.
  3. If released before 2 seconds -> countdown is cancelled, node returns to `NORMAL` (Green).
  4. If held for full 2.0 seconds -> **SOS CONFIRMED**.
  5. RGB LED turns solid **Red**.
  6. BLE manufacturer packet updates risk level to `CRITICAL` (Code 4).
  7. Characteristic notification is dispatched.
  8. Android guardian device receives `CRITICAL` beacon and raises parent emergency alert.
- **Clearing SOS**:
  - Press and hold BOOT for 2.0 seconds again to clear SOS and return to `NORMAL` (Green LED).
  - Alternatively, press `RESET` to reboot the prototype.

---

## 6. Onboard RGB LED State Indicators

| State | Color / Pattern | Meaning |
|---|---|---|
| `BOOT` | White (neutral) | System initializing |
| `NORMAL` | Steady Green | Safe state, active BLE advertising |
| `SOS_COUNTDOWN` | Pulsing Yellow / Orange | Button held, countdown in progress |
| `SOS_ACTIVE` | Solid Urgent Red | Critical SOS triggered |
| `BLE_CONNECTED` | Brief Cyan Flash | Guardian app connected |
| `ERROR` | Rapid Red Blink | Hardware or BLE initialization fault |

---

## 7. Firmware Build, Flash & Diagnostics

### Prerequisites
- Python 3 with `platformio` installed (`pio`).
- USB-C cable connected to `COM11` (or native port `COM8`).

### Build Firmware
```bash
pio run -d hardware/esp32-child-node
```

### Flash to ESP32-S3
```bash
pio run -d hardware/esp32-child-node --target upload --upload-port COM11
```

### Open Serial Monitor (115200 baud)
```bash
pio device monitor -d hardware/esp32-child-node -p COM11 -b 115200
```

### Serial Boot Output Example
```text
=================================
 SafeBand Guardian Child Node
=================================

Chip:
ESP32-S3

Firmware:
1.0.0

Protocol:
SafeBand BLE V2

Node Role:
CHILD

Child ID:
LOCAL_ONLY

BLE:
READY

State:
NORMAL

=================================
[INFO] BOOT button configured on GPIO 0 (hold 2s for SOS)
[INFO] Status RGB LED configured on GPIO 48
[INFO] Ephemeral ID: EP-8C9B (Privacy active)
[BLE] Initializing SafeBand BLE subsystem...
[BLE] SafeBand BLE advertising active!
```

---

## 8. Android App Integration & Verification Flow

1. Open Android SafeBand Guardian App in **Parent Mode**.
2. Tap **"BLE Guardian Scanner"** to activate scanning.
3. The scanner discovers `SafeBand-Child` (`EP-XXXX`).
4. Device appears under **Nearby Child Nodes** as `Physical Wearable Node`.
5. Press and hold the ESP32-S3 `BOOT` button for 2 seconds.
6. Board LED illuminates **Red**.
7. Android app immediately receives `CRITICAL` packet -> plays emergency sound -> opens `EmergencyAlertSheet`.
8. Alert sheet displays `"BLE EMERGENCY BEACON"` (physical event, not simulation).

---

## 9. Known Prototype Limitations

- **No GPS hardware**: Coordinates displayed as Unknown / Proximity BLE only.
- **No IMU**: Anomaly detection must be simulated or added in future revisions.
- **No Battery sensor**: Battery percentage reported as Unavailable.
- **No Cellular / Cloud**: Mesh & local BLE range only.
