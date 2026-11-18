# SafeBand Guardian v3.4.0 Release Notes

**Release Version:** v3.4.0  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 7.0 (API 24) through Android 15 (API 36)  
**Hardware Node:** ESP32-S3 BLE Wearable Node  
**Release Date:** November 2026  
**Git Tag:** `v3.4.0`  
**Commit:** `d0e512c`

---

## 🚀 What's New & Hardened in v3.4.0

### 🔗 1. Feature 1: Parent-Child Provisioning & Connectivity
- **Parent Node Configuration:** Support for configurable network endpoint parameters: IP address, port number, endpoint identifier (`PN-HUB-01`), and auth tokens stored securely via `SafeBandPreferences`.
- **Encrypted BLE GATT Provisioning:** Dedicated GATT Characteristic `00005afe-0002-1000-8000-00805f9b34fb` (`SAFEBAND_CHAR_PROVISION_UUID`). Authenticated transfer format `PROV:childId|parentId|ip|port|endpointId|token|ver`.
- **Persistent ESP32-S3 NVS Flash:** Implementation of `NodeConfig` module utilizing non-volatile flash storage (`Preferences`). Persistent across device reboots, power cuts, and BLE disconnections without storing plaintext secrets.
- **Parent Dashboard Status:** Real-time visual badge tracking: "PROV" / "UNPROV", parent endpoint display, and complete child registration flow.

### 🌐 2. Feature 2: Community-Assisted Emergency Alert Propagation
- **Multi-Type BLE Emergency Alerts:** Wearable broadcasts distinguish between Emergency SOS (`0x03`) and Physical Strap Tamper (`0x04`).
- **Strict Privacy Preservation (Zero PII):** Relaying smartphones detect nearby emergency beacons without exposing or storing child names, photos, medical conditions, or parent phone numbers.
- **Automatic Location Fix Attachment:** Relaying smartphone attaches its own verified Android GPS/Fused Location fix (`relayLatitude`, `relayLongitude`, `relayAccuracy`).
- **Persistent Forwarding Queue (`RelayForwardingRecord`):** Ingested alerts are saved to Room schema v6 with status tracking (`QUEUED`, `DELIVERED_DIRECT`, `DELIVERED_BACKEND`).
- **Dedicated Relay Alert UI:** `RelayEmergencyAlertSheet` displays translucent glass UI with ephemeral broadcast ID and privacy guarantees for good samaritans.
- **Parent Location Distinction:** Dashboard maps explicitly differentiate between the child's last known wearable fix and `"Emergency detected near this location by a participating device."`.

### ⚡ 3. Feature 3: GPIO3 and GPIO4 Hardware Tamper Detection
- **Dynamic Active Continuity Monitoring:** GPIO 3 operates as an active TX pulse generator while GPIO 4 operates as an input with internal pull-down resistor to monitor strap integrity.
- **Debounced 7-State Finite State Machine:** `INITIALIZING` → `PROTECTED` ⇄ `SUSPECTED_TAMPER` → `TAMPER_CONFIRMED` / `AUTHORIZED_DISCONNECTION` / `RESTORING` / `SENSOR_FAULT`. Requires $\ge 3$ consecutive cycles of open-circuit detection (600ms) before confirming tamper.
- **Parent-Authorized Maintenance Mode:** Dedicated GATT Maintenance Characteristic `00005afe-0003-1000-8000-00805f9b34fb`. Guardians authorize strap disconnection for durations between 10s and 3600s with automatic expiration, cancellation support, and persistent state tracking.
- **Immediate Escalation:** Unscheduled physical strap breach triggers full emergency broadcast mode, escalating to community relay and high-priority alarms.

### 🗄️ 4. Data Layer: Room Schema Version 6
- **Database Migration:** Implemented `MIGRATION_5_6` extending `paired_children` with `parentNodeIp`, `parentNodePort`, `parentNodeEndpointId`, `tamperStatus`, `tamperAuthorizedUntil`, `isProvisioned`, and `provisionedAt`.
- **New Entity:** Added `relay_forwarding_records` with dedicated `RelayForwardingDao`.
- **Verified Integrity:** Zero data loss migration confirmed via `DatabaseMigrationTest`.

---

## 📦 Verified Build Artifacts

| Component | Path | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android Release APK** | `app/build/outputs/apk/release/app-release.apk` | **14.1 MB** | `845E2AFBB999DB986B223DF7B4C63B2BB43FAA5BE443678630BEE1165E12AACC` |
| **Android Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | **21.4 MB** | `AC431372B3F9154B1810E229A1EE559005556B9E606CEDDC0CCF858639FD8D9C` |
| **ESP32-S3 Firmware** | `hardware/esp32-child-node/.pio/build/esp32-s3-devkitc-1/firmware.bin` | **920 KB** | `65C373CECCB204D714BC007F874411395C5F7C85836F768DDDFAE6303D98DAA4` |

---

## 🧪 Automated Verification Summary

- **Unit Tests:** 125 of 125 passed (`./gradlew testDebugUnitTest` - 100% success rate in 32.9s)
- **Android Lint:** 0 errors, clean analysis (`./gradlew lintDebug` passed in 2m 35s)
- **ESP32-S3 Build:** PlatformIO build completed successfully (`pio run -d hardware/esp32-child-node`)
- **Git Release Tag:** `v3.4.0` pushed to `origin main`

---

# SafeBand Guardian v3.3.0 Release Notes

**Release Version:** v3.3.0  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 7.0 (API 24) through Android 15 (API 36)  
**Release Date:** November 2026  

---

## 🚀 What's New & Hardened in v3.3.0

### 🛡️ 1. Test Suite Stability & Deadlock Elimination
- **Resolved Test Runner Hang:** Identified and eliminated a critical concurrency deadlock between DataStore async single-writer actors and active flow collectors in `ParentChildIsolationAndAuthTest`.
- **ViewModel Lifecycle Scoping:** Added explicit child job cancellation in `SafeBandViewModel.cleanup()`, ensuring clean coroutine cancellation across sequential Robolectric test executions.
- **100% Test Pass Rate:** Verified all 115 unit tests across 20 test files in **27.65s** without a single failure or skipped test.

### 🔐 2. Role Separation & Child Lockdown Hardening
- **Strict Single-APK Role Isolation:** Maintained a unified APK with strict internal segregation between `AppRole.PARENT` and `AppRole.CHILD`.
- **Master PIN Lockdown:** Child mode strictly rejects arbitrary 6-digit PIN inputs; only the configured parent master PIN or an active 15-minute challenge PIN is accepted.
- **Attempt Rate-Limiting:** Added attempt counters and exponential cooldown periods to prevent brute-force attacks on the child lockscreen.
- **Backup Data Exclusions:** Updated `backup_rules.xml` and `data_extraction_rules.xml` to prevent master PIN and sensitive database credentials from being extracted via unencrypted ADB backups.

### 🔑 3. Google Sign-In & Offline Guest Mode
- **Credential Manager Integration:** Upgraded authentication to `androidx.credentials.CredentialManager` and `com.google.android.libraries.identity.googleid`, extracting real Google ID token claims (`sub`, `email`, `name`, `picture`).
- **Robust Exception Handling:** Handled `NoCredentialException` and user cancellation cleanly without UI crashes.
- **Offline Guest Parent Flow:** Retained 100% functionality for emergency deployments, disaster zones, and un-Googled devices without requiring Google Play Services.

### 📡 4. ESP32-S3 BLE Hardware Protocol Compliance (`0x5AFE`)
- **Preserved Hardware Contract:** Maintained strict byte-level alignment with the ESP32-S3 firmware under little-endian manufacturer ID `0xFE, 0x5A` and magic bytes `'S', 'B'` (`0x53, 0x42`).
- **Multi-Packet Interleaved Rotation:** In emergency mode, packets alternate between Core Telemetry (`0x01`), Child Bio Core (`0x02`), and Guardian Phone (`0x03`) to allow bystander 1-tap assistance without leaking medical details.
- **Honest Sensor Telemetry:** Deprecated fabricated sensor values; explicitly displays `Unavailable` or `Stale` when wearable connection or GPS signal is degraded.

### 🗄️ 5. Room Database Migration Integrity (Schema Version 5)
- Fully verified all Room database migrations (`1→2`, `2→3`, `3→4`, and `4→5`) using Android Room `MigrationTestHelper`.
- Zero data loss across schema upgrades for children profiles, geofences, and tamper-evident SHA-256 audit logs.

---

## 📦 Verified Build Artifacts

| Variant | File Path | File Size | Target SDK | Min SDK |
| :--- | :--- | :--- | :--- | :--- |
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | **22,191,528 bytes (~22.2 MB)** | 36 | 24 |
| **Release APK** | `app/build/outputs/apk/release/app-release.apk` | **14,082,406 bytes (~14.1 MB)** | 36 | 24 |

---

## 🛠️ Verification Verification Commands
To re-verify the codebase independently:

```bash
# Run complete test suite (115 tests)
./gradlew testDebugUnitTest

# Run static analysis
./gradlew lintDebug

# Build release APK
./gradlew assembleRelease
```
