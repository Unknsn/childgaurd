# SafeBand Guardian v3.5.0 — Failure Recovery Matrix
## Wearable, Mobile, and Communication Resilience Architecture

SafeBand Guardian v3.5.0 implements a multi-layer fail-secure architecture across the ESP32-S3 wearable node, Android parent/child application, and community mesh relay infrastructure. This document defines the failure modes, detection mechanisms, fallbacks, recovery actions, and data integrity guarantees.

---

## 1. Wearable Node Failure Scenarios (ESP32-S3)

| ID | Failure Scenario | Trigger / Condition | System Detection Mechanism | Immediate Fallback Behavior | Recovery / Self-Healing Action | User Impact & Alerting | Data Integrity Guarantee |
|---|---|---|---|---|---|---|---|
| **W-01** | **Battery Depletion** | LiPo voltage drops below 3.3V / ADC critical threshold. | Battery monitoring ADC ISR checks voltage every 30s. | Flashes red LED warning pattern; flushes NVS cache and enters deep sleep. | Recharges via USB-C; PMU triggers power-on reset. | Parent notified of `CRITICAL_BATTERY` before device sleeps. | Monotonic sequence counters and NVS provisioning records persisted in non-volatile flash. |
| **W-02** | **Cold Boot / Power Loss** | Wearable powered off (or battery disconnected) and repowered. | System reset vector (`ESP_RST_POWERON` / `ESP_RST_SW`). | **Fail-Secure Boot Policy**: `s_tamperAuthorizedUntil` is immediately set to `0`. Hardware continuity check runs on GPIO3/GPIO4. | Wearable resumes BLE advertising with current strap continuity status. | If strap was disconnected while powered off, enters `TAMPER_DETECTED` and triggers emergency broadcast. | Expired or stale maintenance mode cannot survive reboot; prevents tamper bypass via power-cycle. |
| **W-03** | **Strap Cut While Powered Down** | Strap loop physically severed while device was dead/off. | On boot, GPIO3 emits dynamic pseudo-random challenge pulses; GPIO4 reads low / disconnected. | FSM immediately initializes into `TAMPER_DETECTED` -> `TAMPER_CONFIRMED`. | Begins high-power BLE emergency advertising (`0x5AFE`, `alertType = 0x02`). | Parent and nearby bystanders receive emergency alert immediately upon node power-on. | Tamper flag committed to NVS flash; persists until parent authenticated clear command. |
| **W-04** | **NVS Flash Corruption** | Flash block corruption or power loss during write. | NVS checksum verification failure on boot. | Wearable enters `UNPROVISIONED` state; advertises `0x5AFE` unprovisioned beacon. | Node waits for parent GATT re-provisioning with new monotonic version. | Parent application flags wearable as `NEEDS_PROVISIONING`. | Wearable does not accept unauthorized configuration; rejects stale packets. |
| **W-05** | **BLE Radio Hang / Stack Fault** | NimBLE stack deadlock or buffer starvation. | FreeRTOS Software Watchdog Timer (WDT) triggers after 10s of task inactivity. | Core dumps debug trace to serial; watchdog executes warm restart. | ESP32 reinitializes NimBLE controller; restores advertising within 1.2 seconds. | Temporary gap in advertising (< 2s); parent health monitor marks device `STALE` only if gap exceeds 30s. | Zero state loss; all operational parameters restored from NVS. |

---

## 2. Android Mobile Application Failure Scenarios

| ID | Failure Scenario | Trigger / Condition | System Detection Mechanism | Immediate Fallback Behavior | Recovery / Self-Healing Action | User Impact & Alerting | Data Integrity Guarantee |
|---|---|---|---|---|---|---|---|
| **A-01** | **Process Killed by OS** | Android low-memory killer (LMK) terminates background app. | OS kills process; Foreground Service death detected by OS init. | Foreground Service configured with `START_STICKY` and high-priority notification channel. | Android restarts `BleSafetyMonitorService` automatically; Room database reloaded. | Foreground notification remains pinned; monitoring resumes without parent intervention. | Room SQLite database preserves all queued relay records and device states. |
| **A-02** | **Bluetooth Adapter Disabled** | Parent or child disables Bluetooth in Android Quick Settings. | `BluetoothAdapter.ACTION_STATE_CHANGED` broadcast receiver in `BleSafetyManager`. | UI immediately displays prominent "Bluetooth Disabled" banner; scanning paused. | Listens for `STATE_ON`; automatically restarts BLE scanner upon Bluetooth reactivation. | Parent warned that child safety monitoring is inactive while Bluetooth is disabled. | Queued emergency events preserved; scanner restarts with identical filters. |
| **A-03** | **Location Permissions Revoked** | User revokes `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` in system settings. | Security check in `EmergencyRelayManager.captureCurrentLocation()`. | **Honest Null Telemetry**: Relays emergency payload with `relayLatitude = null`, `relayLongitude = null`. | Relay continues functioning without crashing or faking location; prompts user to grant permission. | Warning dialog displayed explaining that location assistance improves search-and-rescue. | Zero fake coordinates generated; search teams receive honest data rather than false locations. |
| **A-04** | **Airplane Mode Enabled** | Device connectivity turned off completely. | Android `ConnectivityManager` network callbacks (`onLost`). | Relays queued locally in Room database (`RelayForwardingRecord` status `QUEUED`). | When connectivity restored, `NetworkCallback.onAvailable()` automatically invokes `drainForwardingQueue()`. | User notified that alerts are stored and will transmit automatically once online. | All records persisted in Room SQLite; guaranteed zero loss during offline periods. |
| **A-05** | **App Storage Cleared / Reinstalled** | User clears application data or reinstalls app. | Android Keystore key invalidated / Room database fresh. | Database initializes to schema v6 with default state; prompts for parent login. | Parent logs in; credentials fetched and re-encrypted with new Android Keystore AES-256 key. | Parent must re-pair or re-provision child wearable. | Cryptographic keys rotated cleanly; orphaned child wearables reject old credentials. |

---

## 3. Communication & Relay Mesh Failure Scenarios

| ID | Failure Scenario | Trigger / Condition | System Detection Mechanism | Immediate Fallback Behavior | Recovery / Self-Healing Action | User Impact & Alerting | Data Integrity Guarantee |
|---|---|---|---|---|---|---|---|
| **C-01** | **Parent Node Unreachable** | Parent Node IP/Port offline, server down, or network router off. | HTTP `ConnectException`, `SocketTimeoutException`, or non-200 HTTP response. | Record marked `QUEUED`; attempt counter incremented. | Exponential backoff retry worker (`drainForwardingQueue()`) retries with $10s \times 2^{\text{attempts}}$ capped at 5m. | Bystander phone holds payload safely; parent alerted via alternate paths if available. | Payload retained up to 24 hours (`INCIDENT_EXPIRY_MS`), then safely expired to prevent buffer bloat. |
| **C-02** | **DNS Resolution Failure** | Domain name resolution failure for cloud escalation endpoints. | `UnknownHostException` in HTTP client. | Immediate fallback to direct IP endpoint if configured; otherwise queues record. | Retries on network reconnect or DNS cache refresh. | Transparent to bystander; parent app warns of degraded cloud sync. | Zero packet dropping; record stored in local Room SQLite. |
| **C-03** | **HTTP 502/503 Server Errors** | Upstream parent node gateway or cloud server overload. | HTTP status code 502/503/504 returned in HTTP response. | Request not retried immediately; exponential backoff delays next attempt. | Retries scheduled according to backoff policy until server recovers. | Server load protected from thundering herd effect. | Idempotency ensured via unique `incidentId`. |
| **C-04** | **Replay / Spoofing Attack** | Attacker intercepts and replays previous provisioning or maintenance packet. | Monotonic sequence check (`seq <= s_lastMaintenanceSeq`) or HMAC verification failure. | Hardware immediately drops packet and returns `ERR:UNAUTHORIZED` or `ERR:REPLAY`. | Wearable maintains active operational state without interruption. | Attacker cannot clear tamper alarms or modify configuration. | Immutable sequence counters prevent replay of expired authorizations. |
| **C-05** | **Duplicate Relay Packets** | Multiple nearby bystander smartphones detect same wearable SOS. | `EmergencyRelayManager.isDuplicateBroadcast()` memory cache + Room database lookup. | In-memory 60s sliding window deduplicates duplicate broadcasts; ignores repeats. | Parent node server performs secondary deduplication using `incidentId` and `timestamp`. | Cellular bandwidth conserved; parent not spammed with dozens of duplicate alarms. | Single canonical incident created; subsequent updates enrich GPS accuracy if higher. |

---

## 4. Verification and Self-Healing Test Procedures

1. **Simulate Wearable Reboot During Maintenance:**
   - Authorize 15-minute maintenance on child wearable.
   - Power cycle ESP32-S3.
   - Verify serial log indicates `Tamper authorized window reset to 0 (fail-secure reboot)`.
   - Verify strap disconnection immediately triggers tamper alarm.

2. **Simulate Offline Queue Drain:**
   - Put smartphone in Airplane mode.
   - Trigger simulated SOS broadcast.
   - Verify record stored in `relay_forwarding_records` with status `QUEUED`.
   - Re-enable WiFi/Cellular.
   - Verify `drainForwardingQueue()` executes and flushes queued records.

3. **Simulate Permission Revocation:**
   - Revoke Location permission in Android Settings.
   - Broadcast emergency beacon.
   - Verify HTTP payload contains `"relayLatitude":null` and `"relayLongitude":null`.
   - Verify zero crashes and application logs indicate honest fallback.
