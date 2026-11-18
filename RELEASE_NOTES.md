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
