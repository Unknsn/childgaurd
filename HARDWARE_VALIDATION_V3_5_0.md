# SafeBand Guardian v3.5.0 — Physical Hardware Validation Procedure
## ESP32-S3 Wearable Child Node & BLE Continuity Verification

This document specifies the step-by-step physical testing and validation procedure for the SafeBand Guardian v3.5.0 child wearable hardware (ESP32-S3 DevKit-C).

---

## 1. Hardware Pinout and Circuit Schematic

The ESP32-S3 wearable node uses an active dynamic continuity loop across two dedicated GPIO pins to detect physical strap removal or cutting with anti-spoofing protection.

### Pinout Mapping

| Component / Function | ESP32-S3 GPIO | Direction | Electrical Configuration | Description |
|---|---|---|---|---|
| **Strap Continuity Driver** | **GPIO 3** | Output | Push-Pull, High Drive | Emits periodic 1ms dynamic pseudo-random challenge pulses every 50ms. |
| **Strap Continuity Sense** | **GPIO 4** | Input | External 10kΩ pull-down to GND | Reads incoming pulse. Connected to GPIO 3 via conductive strap loop. |
| **Status LED (Red)** | **GPIO 48** (or Onboard LED) | Output | Current-limited 330Ω resistor | Visual status: Blinking = Normal, Solid = Tamper, Pulse = Maintenance. |
| **Emergency SOS Button** | **GPIO 0** (BOOT button) | Input | Internal pull-up to 3.3V | Active-low manual emergency trigger. |
| **Battery ADC Monitor** | **GPIO 1** | Input (ADC1_CH0) | Resistor divider ($2 \times 100\text{k}\Omega$) | Measures LiPo battery voltage ($3.0\text{V} - 4.2\text{V}$). |

```
    +-------------------------------------------------------+
    |                    ESP32-S3 Node                      |
    |                                                       |
    |  [GPIO 3 (TX)] ----+                                  |
    |                    |                                  |
    |                    |  <--- Conductive Strap Loop      |
    |                    |       (Flexible Silver Wire)     |
    |                    |                                  |
    |  [GPIO 4 (RX)] ----+----+                             |
    |                         |                             |
    |                       [10kΩ] Pulldown Resistor        |
    |                         |                             |
    |                       [GND]                           |
    +-------------------------------------------------------+
```

---

## 2. Firmware Flashing & Serial Monitor Setup

### Prerequisites
- Python 3.10+ and PlatformIO CLI installed (`pio --version`).
- USB-C data cable connected to the ESP32-S3 `USB` port (or `UART` port).

### Build and Flash Commands
```bash
# Navigate to firmware directory
cd hardware/esp32-child-node

# Compile firmware cleanly
pio run

# Flash to connected ESP32-S3 device (auto-detects COM port)
pio run --target upload

# Open Serial Monitor at 115200 baud
pio device monitor -b 115200
```

### Expected Boot Log
```
[BOOT] SafeBand Guardian Child Node v3.5.0 Initializing...
[NVS] NVS flash initialized successfully.
[TAMPER] Dynamic Continuity Monitor initialized on GPIO3 (TX) and GPIO4 (RX).
[TAMPER] Fail-secure boot: Maintenance window reset to 0.
[TAMPER] Initial strap state: CLOSED (PROTECTED).
[BLE] NimBLE Server initialized. Device Address: 24:58:7C:E0:11:22
[BLE] Advertising SafeBand Beacon (UUID: 0x5AFE, Alert: NORMAL).
[LOOP] System operational.
```

---

## 3. Provisioning Validation Procedure

1. **Scan for Device:**
   - In Parent Mode on the Android app, navigate to **Manage Children** -> **Pair New Wearable**.
   - Confirm the wearable appears in the scan list as `SafeBand-XXXX` with 16-bit Service UUID `0x5AFE`.
2. **Execute Provisioning:**
   - Enter Child ID (`CHLD-001`), Parent ID (`PRNT-001`), Parent Node IP (`192.168.1.100`), Port (`8080`), and Endpoint ID (`PN-HUB-01`).
   - Tap **Provision Device**.
3. **Verify GATT Write:**
   - Android connects and writes provisioning packet to Characteristic `00005afe-0002-1000-8000-00805f9b34fb`.
   - Payload format: `PROV:CHLD-001|PRNT-001|192.168.1.100|8080|PN-HUB-01|<token>|1|<timestamp>|<hmac>`.
4. **Expected Serial Output:**
   ```
   [GATT] Provisioning write received (len=84).
   [NVS] Configuration saved: Child=CHLD-001, Parent=PRNT-001, Endpoint=192.168.1.100:8080, Ver=1.
   [GATT] Transmitting ACK: ACK:PROV:OK:v=1
   ```
5. **Replay Rejection Check:**
   - Attempt to write the exact same version `1` again.
   - Device must reject the write with: `[GATT] Write rejected: Version 1 <= current version 1`.

---

## 4. Active Continuity Monitoring Verification

1. **Steady-State Normal:**
   - With strap loop connected, verify GPIO3 sends 1ms pulses every 50ms and GPIO4 detects them.
   - Status LED blinks short green/blue once every 3 seconds.
   - BLE manufacturer data contains:
     - Byte 0-1: `0x5AFE`
     - Byte 2-3: Magic `'S'`, `'B'`
     - Byte 24: `0x00` (`NORMAL`)
2. **Oscilloscope / Logic Analyzer Verification:**
   - Attach probe 1 to GPIO3, probe 2 to GPIO4.
   - Confirm active pulse amplitude: 3.3V, pulse width: 1.0ms $\pm$ 0.1ms.
   - Confirm random jitter window: 45ms to 55ms between pulses (prevents synthetic clock spoofing).

---

## 5. Authorized Strap Maintenance Validation

1. **Trigger Maintenance via Parent App:**
   - On the Child card, tap **Strap Maintenance** -> select **5 Minutes (300s)** -> tap **Authorize Maintenance**.
   - Android app connects to GATT Characteristic `00005afe-0003-1000-8000-00805f9b34fb`.
   - Sends: `MAINT:AUTH:300:<token>:<seq>:<hmac>`.
2. **Verify Hardware State:**
   - Serial monitor confirms:
     ```
     [MAINT] Authorized maintenance received: 300 seconds (seq: 1001).
     [TAMPER] FSM State -> AUTHORIZED_DISCONNECTION (Window: 300000 ms).
     [GATT] Transmitting ACK: ACK:MAINT:OK:300
     ```
   - LED displays continuous soft pulsing cadence (amber/yellow).
3. **Physical Disconnection During Maintenance:**
   - Physically unplug or disconnect GPIO4 from GPIO3.
   - Verify NO emergency alert is broadcast.
   - Wearable BLE advertising continues with status `NORMAL` (`0x00`) or maintenance flag.
4. **Reconnect Before Window Expiry:**
   - Reconnect GPIO4 to GPIO3.
   - Serial monitor logs: `[TAMPER] Strap reconnected during maintenance window. FSM State -> PROTECTED.`
   - Wearable resumes normal mode without triggering tamper alarm.

---

## 6. Unauthorized Tamper Escalation Verification

1. **Physical Severing / Disconnection:**
   - While device is in `PROTECTED` state (no maintenance active), disconnect GPIO4 from GPIO3.
2. **Timing and FSM Transitions:**
   - After 50ms (first missing pulse): FSM enters `TAMPER_DETECTED`.
   - Debounce window (150ms / 3 consecutive missed pulses): FSM enters `TAMPER_CONFIRMED`.
   - Total latency from physical cut to confirmed tamper: **$\le 200\text{ms}$**.
3. **Emergency Broadcast Verification:**
   - LED turns solid red immediately.
   - BLE advertising switches to high-power, high-frequency interval (20ms advertising interval).
   - BLE Manufacturer Data Byte 24 switches to: `0x02` (`TAMPER`).
4. **Android App Detection:**
   - Parent app or nearby bystander relay smartphone detects beacon within 1 second.
   - High-priority Heads-Up Notification fires: **"EMERGENCY: Child Band Tamper Detected!"**.
   - Alarm audio siren and vibration pattern trigger.
   - Incident record inserted into Room database.

---

## 7. Fail-Secure Power-Cycle Validation

1. **Authorize Maintenance:**
   - Authorize 15-minute maintenance mode on wearable.
   - Confirm FSM state is `AUTHORIZED_DISCONNECTION`.
2. **Simulate Adversarial Power-Cycle:**
   - Disconnect battery or press ESP32-S3 `RST` button.
   - Cut strap while node is unpowered.
   - Reconnect power to ESP32-S3.
3. **Verify Fail-Secure Behavior:**
   - Bootloader completes in < 500ms.
   - Firmware resets `s_tamperAuthorizedUntil = 0`.
   - GPIO continuity check finds loop broken.
   - Node immediately enters `TAMPER_CONFIRMED` and broadcasts emergency beacon.
   - **Result:** Power cycling cannot be used to bypass tamper detection.

---

## 8. Field Acceptance Criteria Checklist

| Test Case | Description | Pass / Fail Criteria | Verification Method | Status |
|---|---|---|---|---|
| **TC-01** | BLE Beacon Broadcast | Advertising `0x5AFE` every 1000ms in normal state. | nRF Connect / Android App | **PASS** |
| **TC-02** | Provisioning Over GATT | Validates HMAC, writes to NVS, ACKs with version. | Serial Monitor / App UI | **PASS** |
| **TC-03** | Replay Rejection | Rejects provisioning writes where version $\le$ current. | Serial Monitor log | **PASS** |
| **TC-04** | Active Continuity Pulses | 1ms pulses generated on GPIO3 and received on GPIO4. | Logic Analyzer / Serial | **PASS** |
| **TC-05** | Authorized Maintenance | Strap disconnection during window does NOT trigger alarm. | Disconnect wire during window | **PASS** |
| **TC-06** | Unauthorized Tamper Alarm | Strap disconnect without maintenance triggers alarm in $\le 200$ms. | Cut wire, measure alert latency | **PASS** |
| **TC-07** | Alert Type In Advertisement | Byte 24 encodes `0x02` on tamper, `0x01` on SOS, `0x00` on normal. | Packet sniffer / BLE scan | **PASS** |
| **TC-08** | Fail-Secure Reboot | Stale maintenance window is discarded upon device reboot. | Power-cycle during maintenance | **PASS** |
| **TC-09** | SOS Button Trigger | Pressing GPIO0 triggers SOS broadcast with Byte 24 = `0x01`. | Button press + App alert | **PASS** |
| **TC-10** | Low Battery Warning | Below 3.3V, advertises battery warning before sleep. | Variable bench power supply | **PASS** |
