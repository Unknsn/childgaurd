# SafeBand Guardian v4.0.0 — Security Review & Threat Analysis

## 1. Executive Summary

SafeBand Guardian v4.0.0 handles sensitive child geolocation and physical safety alarms. This security review details the threat modeling, defense-in-depth architecture, cryptographic controls, and verification practices implemented across the embedded hardware, Android mobile app, laptop backend, and public transit tunnel.

---

## 2. Threat Modeling & Mitigations (STRIDE Matrix)

| Threat Category | Identified Attack Vector | Implemented Mitigation in v4.0.0 |
| :--- | :--- | :--- |
| **Spoofing** | Adversary attempts to forge child location or send fake SOS alarms. | All client API requests require cryptographically validated Google OAuth2 tokens or signed JWTs. ESP32 pairing requires challenge-response proof. |
| **Tampering** | Child or attacker cuts strap or shorts tamper loop pins to bypass detection. | Dynamic 200µs continuity test pulse between GPIO 3 and GPIO 4 with internal pull-down resistor prevents static pull-up/pull-down bypass. |
| **Repudiation** | User denies acknowledging an incident or triggering emergency test. | Comprehensive `audit_logs` table logs actor UUID, action, target entity, timestamp, and IP address for all lifecycle events. |
| **Information Disclosure** | Eavesdropper monitors BLE broadcasts or sniffs public internet traffic to track child movements. | Public cloud traffic is encrypted via Cloudflare TLS 1.3. BLE emergency beacons contain only one-way truncated hashes; zero PII is broadcast. |
| **Denial of Service** | Malicious actor floods relay endpoint with fake panic beacons. | Backend applies sliding-window deduplication (15s) and per-observer rate limiting (10 requests/min). |
| **Elevation of Privilege** | Normal user attempts to view or alter another family's child profiles. | Object-level access control: JPA queries strictly filter by authenticated `parentId` from Spring Security context. |

---

## 3. Cryptographic Implementations

### 3.1. Google OAuth2 Token Validation
`GoogleTokenVerifierService` validates Google ID tokens using Google's official RSA public certs (`https://www.googleapis.com/oauth2/v3/certs`). Only tokens with `aud == GOOGLE_CLIENT_ID` and unexpired timestamps are converted into local parent sessions.

### 3.2. JWT Secret & Token Lifecycle
* **Access Tokens**: HMAC-SHA256 signed tokens containing user UUID, email, and roles, with a 1-hour expiration.
* **Refresh Tokens**: Cryptographically random 64-character tokens with a 30-day sliding expiration, stored hashed in the backend database.
* **Configuration**: `JWT_SECRET` must be at least 256 bits (32 characters); default development keys trigger warning logs and are overridden via environment variables.

### 3.3. BLE Data Protection (AES-128-GCM)
* 128-bit symmetric key negotiated during pairing and held securely in ESP32 NVS.
* Payloads utilize 12-byte initialization vectors and 16-byte authentication tags to ensure confidentiality and detect ciphertext manipulation.

---

## 4. Hardware Security Architecture

* **Physical Continuity Loop**: Dedicated GPIO 3/4 circuit monitored by active pulsed state machine.
* **Firmware Flash Protection**: ESP32-S3 firmware supports secure boot v2 and flash encryption on production hardware.
* **Maintenance Mode Timeout**: Authorized disarming of tamper alerts requires an authenticated BLE GATT write and automatically re-arms after 300 seconds to prevent accidental disarming.
