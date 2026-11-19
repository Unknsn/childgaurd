# SafeBand Guardian v4.0.0 — Device Pairing Test Results

## 1. Scope & Verification Strategy

This report verifies the hardware pairing protocol between the ESP32-S3 wearable node, the Android parent app, and the Spring Boot backend service.

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **PAIR-01** | Initiate pairing session via `POST /api/v1/pairing/sessions` | Generates 32-byte cryptographically secure session nonce | Automated integration test | **PASS** |
| **PAIR-02** | Path aliasing via `POST /api/v1/pairing/initiate` | Returns identical pairing session response (HTTP 200) | Automated integration test | **PASS** |
| **PAIR-03** | Submit valid HMAC challenge proof via `POST /api/v1/pairing/{sessionId}/complete` | Confirms session, links device MAC to child, returns 200 OK | Automated integration test | **PASS** |
| **PAIR-04** | Complete pairing with expired session (> 300s) | Rejected with HTTP 400 Session Expired | Backend unit test | **PASS** |
| **PAIR-05** | Complete pairing with invalid challenge proof | Rejected with HTTP 400 Invalid Proof | Backend unit test | **PASS** |
| **PAIR-06** | Pair device already bound to another child profile | Prevents duplicate association, returns conflict error | Backend unit test | **PASS** |
| **PAIR-07** | BLE GATT handshake writing nonce to `0x5AFE-0002` | ESP32 calculates HMAC proof and presents on read | Embedded firmware test | **SIMULATED PASS** (Physical bench pending) |
| **PAIR-08** | Pairing state persistence across ESP32 power reboot | NVS partition preserves bound parent identity | Firmware NVS unit test | **SIMULATED PASS** (Physical bench pending) |

---

## 3. Findings & Physical Verification Status

* **Software & Backend**: 100% verified via automated integration tests in `BackendApiIntegrationTest.kt`.
* **Hardware Bench**: Simulated in firmware unit tests; physical bench verification with a physical ESP32-S3 module and real BLE GATT transactions remains marked as `SIMULATED PASS`.
