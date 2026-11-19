# SafeBand Guardian v4.0.0 — BLE Communication Test Results

## 1. Scope & Verification Strategy

This report verifies the byte-level synchronization and advertisement packet parsing between the ESP32-S3 wearable node and the Android BLE scanner stack (`BleSafetyManager.kt`).

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **BLE-01** | Decode normal heartbeat packet (`0x00`) | Parsed as `NORMAL` heartbeat, updates battery & sequence | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-02** | Decode manual SOS panic packet (`0x02`) | Parsed as `MANUAL_SOS`, risk level escalated to `CRITICAL` | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-03** | Decode strap cut tamper packet (`0x03`) | Correctly maps `alertType = "TAMPER"`, sets `AlertFlag.TAMPER` | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-04** | Sequence counter monotonically increasing | Rejects duplicate sequence numbers within replay window | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-05** | Truncated advertisement (< 26 bytes) | Safely dropped with warning log; zero crash or buffer overflow | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-06** | Invalid manufacturer company identifier (!= `0x5AFE`) | Ignored by scanner filter; prevents non-SafeBand packet parsing | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-07** | Protocol version mismatch (!= `0x04`) | Packet safely dropped | `BleSafetyManagerTest.kt` | **PASS** |
| **BLE-08** | Advertising interval acceleration on emergency (200ms) | Verified in firmware state machine | PlatformIO code verification | **PASS** |
| **BLE-09** | BLE recovery after Bluetooth adapter toggle | Scanner automatically rebinds and resumes monitoring | `HardwareSafetyAndBackgroundAlertTest.kt` | **PASS** |

---

## 3. End-to-End Pipeline Integrity

Verified that decoded alert flags remain 100% intact through:
ESP32 Firmware -> BLE Advertisement -> Android BLE Scanner -> `BleSafetyManager` -> `SafeBandViewModel` -> Room DB -> UI Alarm Sheet.
No stage converts a critical tamper or SOS alert into a normal heartbeat.
