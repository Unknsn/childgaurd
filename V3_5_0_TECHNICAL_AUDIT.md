# SafeBand Guardian v3.5.0 Technical Architecture & Security Audit

**Audit Date:** November 2026  
**Auditor Role:** Lead Software Architect, Embedded Systems Engineer, Cybersecurity Specialist, QA Lead  
**Baseline Git Commit:** `6ef52e6`  
**Target Release:** `v3.5.0`  
**Repository Package:** `com.aistudio.safeband.xqmt`  

---

## 1. Executive Summary & Audit Baseline

SafeBand Guardian is an event-based child safety ecosystem providing low-power BLE wearable safety monitoring, community-assisted emergency relay, parent-authorized strap maintenance, and physical tamper detection.

The reported baseline `v3.4.0` successfully integrated Room Schema v6, PlatformIO ESP32-S3 firmware build scripts, GATT characteristic handlers, and initial community relay records. However, this rigorous audit of the code (`app/src/main` and `hardware/esp32-child-node/src`) reveals significant security, privacy, and architectural gaps that must be remediated for `v3.5.0` production deployment.

---

## 2. Component-by-Component Audit Findings

### 2.1 BLE Provisioning Protocol (`BleProvisioningManager.kt` & `safeband_ble.cpp`)
- **Current State:** Provisioning payload string `PROV:childId|parentId|ip|port|endpointId|token|ver` is transmitted over GATT characteristic `00005afe-0002-...`.
- **Vulnerabilities Identified:**
  1. *Plaintext in Transit:* Long-lived authentication tokens and parent network IPs are exposed over unencrypted BLE GATT writes.
  2. *Replay Attacks:* No monotonic sequence numbers or challenge nonces exist in the payload. A recorded write can be replayed to overwrite endpoint configuration.
  3. *Unauthorized Overwrite:* If `authToken` is blank or unprovisioned, `NodeConfig::verifyAuthToken` allows arbitrary writes. Once provisioned, there is no cryptographic signature verifying that the write originated from the authorized parent.
- **Remediation for v3.5.0:**
  - Introduce HMAC-SHA256 integrity verification: `PROV:childId|parentId|ip|port|endpointId|token|ver|timestamp|nonce|hmac`.
  - Enforce monotonic version check (`configVersion > currentVersion`).
  - Add session expiration window (TTL 5 minutes).

### 2.2 GPIO3 & GPIO4 Hardware Tamper Detection (`tamper_monitor.cpp` & `BleTamperMaintenanceManager.kt`)
- **Current State:** GPIO 3 outputs active dynamic toggle pulses; GPIO 4 reads pulses with internal pull-down. 7-state FSM debounces continuity over $\ge 3$ cycles (600ms).
- **Vulnerabilities & Reliability Gaps:**
  1. *Plaintext Maintenance Command:* `MAINT:AUTH:<sec>:<token>` sends the parent token in cleartext. Anyone sniffing the BLE connection can capture the token.
  2. *Lack of Replay Protection in Maintenance:* Maintenance command lacks sequence numbers or timestamps. An attacker can replay `MAINT:AUTH:3600:...` to keep tamper detection disabled indefinitely.
  3. *Reboot State Safety:* In `node_config.cpp`, `t_auth_until` is stored as an integer timestamp. If the wearable device reboots without an RTC or internet time synchronization, `millis()` restarts from 0, causing time comparison anomalies (`now >= s_maintenanceExpiryMs`).
- **Remediation for v3.5.0:**
  - Secure maintenance command with monotonic sequence number and HMAC: `MAINT:AUTH:<duration>:<seq>:<hmac>`.
  - Fail-secure reboot semantics: On reboot, default to `TAMPER_PROTECTED` and verify physical continuity. Discard stale maintenance windows if relative uptime clock has reset.
  - Add active watchdog safety to prevent watchdog resets during debounce loops.

### 2.3 Community Emergency Relay & Location Privacy (`EmergencyRelayManager.kt`)
- **Current State:** Relaying smartphones detect emergency beacons, record an entry in Room table `relay_forwarding_records`, and POST to the parent node endpoint.
- **Vulnerabilities & Reliability Gaps:**
  1. *Violation of "No Fake GPS" Rule:* In `captureCurrentLocation()`, if location permission is missing or GPS fix fails, it returns hardcoded San Francisco coordinates `Pair(37.7749, -122.4194)`. This violates Rule 3 (No fake functionality) and can misdirect search & rescue efforts.
  2. *Missing SOS vs Tamper in Advertising Packet:* `safeband_ble.cpp` builds a 24-byte payload that omits `alertType`. As a result, `BleSafetyMonitorService.kt` falls back to checking `payload.deviceId.contains("TAMPER")` (which is never true for ephemeral IDs `EP-xxxx`), causing all alerts to be treated generically as SOS.
  3. *Unauthenticated Parent Node Dispatch:* HTTP POST to `http://$parentIp:$parentPort/api/relay/alert` does not include bearer tokens or HMAC headers.
  4. *Lack of Background Queue Draining:* If the relay device is offline when an emergency occurs, the alert is stored as `QUEUED`, but there is no background network recovery receiver or exponential backoff worker to dispatch the queue once internet connectivity returns.
- **Remediation for v3.5.0:**
  - Replace fake GPS fallback with honest `null` location handling (`relayLatitude = null`, `relayLongitude = null`). Forward alerts regardless of location availability.
  - Encode `alertType` (Byte 24) in the BLE advertising packet and decode it in `BleSafetyManager.kt`.
  - Implement durable background queue drain worker with exponential backoff and network connectivity change listener.
  - Attach authentication token/signature in HTTP dispatch headers.

### 2.4 Data Storage & Android Keystore (`SafeBandPreferences.kt` & Room)
- **Current State:** Schema version 6 stores parent node endpoint IP, port, token, and tamper status.
- **Vulnerabilities Identified:**
  1. Plaintext storage of `auth_token` in SharedPreferences/DataStore.
  2. Android backup rules must ensure private wearable encryption keys are not exported.
- **Remediation for v3.5.0:**
  - Protect parent auth tokens and master PIN with Android Keystore-backed AES-256-GCM encryption.

### 2.5 Parent & Child UI/UX
- **Current State:** Parent Dashboard displays child cards with "PROV" / "UNPROV" badges.
- **Identified Deficiencies:**
  1. Device health status is static: if a child wearable is turned off or goes out of range, the UI does not distinguish "Connected" vs "Stale" vs "Disconnected".
  2. Emergency alert map does not render the accuracy radius for relay-reported locations.
- **Remediation for v3.5.0:**
  - Implement a 3-tier device health model: `CONNECTED` (<30s), `STALE` (30s–5m), `OFFLINE` (>5m) with exact last-communication timestamps.
  - Update `EmergencyAlertSheet` to render accuracy circles for relay coordinates.

---

## 3. Risk & Technical Debt Matrix

| Risk ID | Severity | Category | Description | Proposed Mitigation |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | **CRITICAL** | Security | Plaintext GATT provisioning payload allows sniffing and unauthorized configuration overwrite. | HMAC-SHA256 authenticated provisioning with monotonic versioning. |
| **SEC-02** | **HIGH** | Security | Maintenance authorization commands are vulnerable to replay attacks. | Include sequence number + HMAC signature; fail-secure reboot policy. |
| **DAT-01** | **HIGH** | Integrity | Hardcoded San Francisco GPS fallback `(37.7749, -122.4194)` on location failure. | Eliminate fake coordinates; persist `null` and forward alert with honesty flag. |
| **REL-01** | **HIGH** | Reliability | `alertType` (SOS vs Tamper) omitted from BLE manufacturer broadcast packet. | Encode `alertType` in Byte 24 of BLE advertising packet. |
| **NET-01** | **MEDIUM** | Network | Queued relay forwarding records are not automatically drained on network reconnect. | Implement durable `RelayQueueDrainWorker` and network connectivity callbacks. |
| **UI-01** | **LOW** | UX | Misleading device online status when wearable is disconnected or powered off. | Introduce dynamic connection health states with last-seen timestamps. |

---

## 4. Audit Conclusion & Action Plan

The v3.4.0 baseline provides the foundational building blocks, but security hardening, honest telemetry enforcement, and end-to-end reliability mechanisms must be implemented before v3.5.0 release.

Phase 2 through Phase 14 will address all issues identified in this audit.
