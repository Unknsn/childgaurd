# SafeBand Guardian v4.0.0 — Pairing & Provisioning Protocol

## 1. Objectives & Threat Model

The Pairing Protocol establishes mutual cryptographic trust between the Parent Android Device, the Laptop Backend, and the ESP32-S3 Wearable Node.

### Security Guarantees:
1. **Replay Protection**: Nonce-based challenge-response prevents replay of captured pairing frames.
2. **Key Secrecy**: The AES-128 master key is never transmitted over plaintext BLE.
3. **Hardware Non-Volatile Binding**: Once paired, the ESP32 stores parent identity in NVS and refuses pairing attempts from third-party devices unless physically reset via maintenance mode.

---

## 2. Pairing Sequence Flow

```
[Parent Android App]               [Laptop Backend]                [ESP32-S3 Wearable]
        |                                 |                                 |
        | 1. POST /api/v1/pairing/initiate|                                 |
        |    (childId, deviceId)          |                                 |
        |-------------------------------->|                                 |
        | 2. Returns sessionNonce         |                                 |
        |<--------------------------------|                                 |
        |                                 |                                 |
        | 3. Connect GATT & Write Characteristic 0x5AFE-0002                |
        |    Payload: {sessionNonce, childIdHash}                           |
        |------------------------------------------------------------------>|
        |                                 |                                 |
        | 4. ESP32 Computes Response:     |                                 |
        |    proof = HMAC-SHA256(nonce, hardwareSecret)                    |
        | 5. Read Characteristic 0x5AFE-0002 (proof)                        |
        |<------------------------------------------------------------------|
        |                                 |                                 |
        | 6. POST /api/v1/pairing/confirm |                                 |
        |    (sessionId, proof)           |                                 |
        |-------------------------------->|                                 |
        |                                 | 7. Backend Validates Proof &    |
        |                                 |    Binds Device to Child Profile|
        | 8. Pairing Confirmed (200 OK)   |                                 |
        |<--------------------------------|                                 |
        |                                 |                                 |
        | 9. Write GATT 0x5AFE-0001 (Finalize Provisioning)                 |
        |------------------------------------------------------------------>|
        |                                 | 10. ESP32 Writes NVS & Flashes  |
        |                                 |     Green Success LED           |
```

---

## 3. QR Code Backup Flow

When direct BLE advertisement discovery is constrained by RF interference or Android Bluetooth permission delays:
1. ESP32 node outputs a dynamic pairing payload via Serial/OLED display (or printed base sticker):
   `SAFEBAND://pair?v=4&dev=SB-A1B2C3D4&sec=8f9a2b...`
2. Parent application uses built-in camera QR scanner to instantly decode device ID and hardware pre-shared secret.
3. App connects directly to the designated MAC address without performing wide-band scanning.

---

## 4. Verification & Validation Rules

* **Timeout**: Pairing sessions automatically expire on the backend after 300 seconds (5 minutes).
* **Single Association**: An ESP32 device ID can be actively associated with only one child profile at a time. Re-association requires explicit parent unbinding or backend admin intervention.
* **Firmware NVS Wipe**: Holding the physical wearable button for 15 seconds during boot triggers an NVS wipe, resetting the device to factory unassociated state.
