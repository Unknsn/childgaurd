# SafeBand Guardian v3.5.0 — Cybersecurity Architecture & Security Review
## Comprehensive Threat Modeling, Cryptographic Auditing, and Privacy Verification

SafeBand Guardian v3.5.0 introduces comprehensive security hardening across Android application boundaries, BLE radio interfaces, ESP32-S3 firmware, and the community emergency relay network. This document provides a formal security audit and threat model evaluation.

---

## 1. System Boundaries and Trust Architecture

The SafeBand Guardian ecosystem operates across four primary trust zones:

```
+-----------------------------------------------------------------------------------+
| [ZONE 1: High Trust] Parent Device                                                |
| - Android Keystore hardware-backed AES-256-GCM encryption.                        |
| - Parent Node Auth Token and Child configuration storage.                         |
| - Full child identity, guardian phone, medical conditions, and handover log.     |
+-----------------------------------------------------------------------------------+
                                  |
               BLE GATT / HMAC    |   Local Wi-Fi / HTTPS
                                  v
+-----------------------------+       +---------------------------------------------+
| [ZONE 2: Secure Node]       |       | [ZONE 3: Authenticated Gateway]             |
| ESP32-S3 Child Wearable     |       | Parent Node / Institution Hub / Cloud       |
| - Dynamic continuity FSM.   |       | - Ingestion endpoint for emergency alerts.  |
| - Non-volatile NVS flash.   |       | - Token-based authentication (Bearer).      |
| - Fail-secure boot policy.  |       | - Multi-child incident aggregation.         |
+-----------------------------+       +---------------------------------------------+
               |                                      ^
               | BLE Advertisement                    | HTTPS / HTTP POST
               | (0x5AFE, No PII)                     | (Anonymized Relay)
               v                                      |
+-----------------------------------------------------+-----------------------------+
| [ZONE 4: Zero-Trust Untrusted Mesh] Nearby Bystander Relay Smartphones            |
| - Detects wearable 0x5AFE BLE advertisements.                                     |
| - Forwards emergency telemetry (Broadcast ID, alert type, GPS, timestamp).       |
| - STRICT ZERO PII: Cannot view child name, parent phone, or medical history.      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Threat Modeling and Attack Vector Analysis

| Attack Vector | Threat Category (STRIDE) | Attack Mechanism | v3.5.0 Hardening & Mitigation | Residual Risk Level |
|---|---|---|---|---|
| **Plaintext GATT Sniffing & Replay** | Information Disclosure / Spoofing | Attacker with BLE sniffer captures provisioning packet and replays it to take over wearable. | **Mitigated**: Provisioning payload includes timestamp, monotonic configuration version ($v_{\text{new}} > v_{\text{current}}$), and HMAC-SHA256 signature calculated over all fields using pre-shared token. Replay packets with identical or lower versions are rejected. | **LOW** |
| **Maintenance Command Replay** | Tampering / Elevation of Privilege | Attacker captures parent's `MAINT:AUTH:300` command and re-transmits it later to suppress tamper alarms while cutting strap. | **Mitigated**: Maintenance commands incorporate a strictly monotonic sequence number ($seq > lastSeq$) and HMAC-SHA256 signature (`MAINT:AUTH:dur:tok:seq:hmac`). Wearable hardware enforces monotonic sequence validation. Replayed commands fail sequence check. | **LOW** |
| **Adversarial Power-Cycle Bypass** | Tampering | Attacker authorizes maintenance mode, powers down device, cuts strap while off, then powers on expecting maintenance window to remain active. | **Mitigated**: **Fail-Secure Boot Policy**. On boot, `s_tamperAuthorizedUntil` is reset to 0 in RAM. Relative `millis()` resets. Any strap break detected on boot immediately enters `TAMPER_CONFIRMED`. | **NEGLIGIBLE** |
| **Synthetic Clock / Voltage Injection** | Tampering / Spoofing | Attacker injects static DC voltage (3.3V) into GPIO 4 to spoof strap continuity while cutting loop. | **Mitigated**: Active dynamic challenge continuity. GPIO 3 emits 1ms pseudo-random pulses with timing jitter (45ms–55ms); GPIO 4 verifies pulse transitions. Static 3.3V or GND triggers immediate tamper alarm. | **LOW** |
| **PII Harvest from Relay Traffic** | Information Disclosure | Malicious bystander operates relay phone to harvest identities, names, or addresses of nearby children. | **Mitigated**: **Strict Privacy Guarantee**. Public BLE advertisements and relay payloads contain ZERO PII. Identifiers are limited to ephemeral or device-level IDs (`EP-XXXX`). Real identities reside only in Parent Phone and authenticated Parent Node. | **NEGLIGIBLE** |
| **Relay Denial-of-Service / Flooding** | Denial of Service | Attacker broadcasts thousands of fake BLE beacons to exhaust bystander battery or parent node network. | **Mitigated**: In-memory 60-second deduplication cache (`EmergencyRelayManager.isDuplicateBroadcast`), exponential backoff on dispatch, and maximum queue size cap. | **LOW** |
| **Credential Theft from Device Storage** | Information Disclosure | Malware or extraction tool reads SQLite database or Shared Preferences from parent phone storage. | **Mitigated**: Parent Node Auth Token encrypted at rest using **Android Keystore AES-256-GCM** with hardware-backed master key. Room database protected by standard OS sandbox. | **LOW** |
| **Fabricated Search & Rescue Locations** | Integrity Violation | Relay sends synthetic coordinates (e.g. San Francisco default) when GPS is offline, misleading responders. | **Mitigated**: Removed all fake coordinate fallbacks. System transmits honest `null` latitude and longitude when GPS fix is missing. Late-arriving GPS fixes enrich existing incidents. | **NEGLIGIBLE** |

---

## 3. Cryptographic Architecture and Verification

### 3.1 Android Keystore AES-256-GCM Encryption
- **Key Alias:** `SafeBandMasterKey`
- **Algorithm:** AES/GCM/NoPadding (256-bit key)
- **Key Generation:** `KeyGenParameterSpec.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)` inside Android hardware-backed keystore (TEE / StrongBox).
- **IV:** SecureRandom 12-byte initialization vector prepended to ciphertext.
- **Payload Format:** `Base64( IV [12 bytes] + Ciphertext + GCM Tag [16 bytes] )`.

### 3.2 HMAC-SHA256 Authenticity & Integrity
- **Key Derivation:** Pre-shared authentication token configured between parent and wearable.
- **Mac Generation:** Standard RFC 2104 HMAC with `HmacSHA256` engine.
- **Constant-Time Verification:** Implementation uses constant-time byte-by-byte comparison (`verifyHmac`) using bitwise OR accumulator (`result or (a[i] xor b[i])`) to prevent timing side-channel attacks:
  ```kotlin
  fun verifyHmac(data: String, key: String, expectedHmac: String): Boolean {
      val actualHmac = computeHmac(data, key)
      if (actualHmac.length != expectedHmac.length) return false
      var diff = 0
      for (i in actualHmac.indices) {
          diff = diff or (actualHmac[i].code xor expectedHmac[i].code)
      }
      return diff == 0
  }
  ```

### 3.3 Monotonic Sequence and Version Protection
- **Provisioning Version:** Monotonic integer version incremented by parent on each configuration push. Child node validates `newVersion > currentVersion` before committing to NVS flash.
- **Maintenance Sequence:** Monotonic sequence counter incremented on every authorization. Child node validates `seq > s_lastMaintenanceSeq` to prevent replay of captured authorization frames.

---

## 4. Privacy Audit & Data Minimization Verification

### Data Stored on Parent Device (Zone 1 - High Trust)
- Child Full Name, Age, Blood Type.
- Guardian Phone Numbers, Emergency Contacts.
- Medical Conditions, Allergies (Optional, parent consented).
- Room Database schema v6, encrypted at OS level.

### Data Stored on Child Wearable (Zone 2 - Embedded Device)
- Child ID (`CHLD-XXXX`), Parent ID (`PRNT-XXXX`).
- Parent Node IP, Port, Endpoint ID.
- Pre-shared Authentication Token (stored in ESP32 NVS flash).
- **ZERO personal identity data (no names, no medical details, no phone numbers).**

### Data Broadcasted over BLE / Relayed via Bystanders (Zones 3 & 4)
- Wearable Device ID / Broadcast ID (`EP-XXXX`).
- Event Type (`SOS`, `TAMPER`, `PROXIMITY_LOST`, `NORMAL`).
- Relaying Phone GPS Coordinates (`lat`, `lon`, `accuracy`) or `null` if unavailable.
- Detection Timestamp.
- **ZERO Child PII. Bystanders cannot identify the child or access private details.**

---

## 5. Production Hardening Recommendations (v4.0 Roadmap)

1. **ESP32-S3 Hardware Security Fuses:**
   - Burn `DIS_BOOT_REMAP` and enable `FLASH_ENCRYPTION` (AES-XTS 256-bit).
   - Enable `SECURE_BOOT_V2` with RSA-3072 signing keys to prevent unauthorized firmware modification.
2. **Rotating BLE MAC Addresses:**
   - Transition from static/public MAC addressing to Resolvable Private Addresses (RPA) regenerated every 15 minutes, resolvable only by the parent phone using Identity Resolving Keys (IRK).
3. **Elliptic-Curve Diffie-Hellman (ECDH):**
   - Implement Curve25519 / X25519 ephemeral key exchange during initial BLE pairing to establish forward-secret shared keys without transmitting tokens over BLE.
