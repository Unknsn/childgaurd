# SafeBand Guardian v4.0.0 Release Notes

**Release Version:** v4.0.0 (Enterprise Cloud Backend, Google Auth, Automated Tunnel, and Real-Time Safety Ecosystem)  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 8.0 (API 26) through Android 15 (API 35)  
**Hardware Node:** ESP32-S3 BLE Wearable Node  
**Backend Platform:** Spring Boot 3.4.2 / Kotlin 2.2.10 (Embedded H2 / PostgreSQL 16)  
**Release Date:** November 2026  
**Git Tag:** `v4.0.0`  

---

## 🌟 What's New in v4.0.0: Public Cloud Backend, Mobile Mesh & Hardware Safety

### 🌐 1. High-Performance Spring Boot Backend
- **Kotlin 2.2.10 & Spring Boot 3.4.2**: Complete modern reactive backend service housed in `:backend` multi-project module.
- **Dual Persistence Strategy**:
  - `standalone` profile: Zero-dependency embedded H2 file database (`./data/safeband`) in PostgreSQL mode with Flyway schema compatibility for instant developer spins.
  - `postgres` profile: Direct PostgreSQL connection via environment variables for cloud cluster scale.
- **Flyway Database Migrations**: Automated V1 schema migration creating 10 relational tables: `users`, `child_profiles`, `wearable_devices`, `pairing_sessions`, `location_breadcrumbs`, `geofence_zones`, `incident_reports`, `relay_observations`, `notification_records`, and `audit_logs`.
- **WebSocket Push Broker**: Real-time `/ws/parent` endpoint broadcasting instant `INCIDENT_CREATED` and `LOCATION_UPDATE` events directly to parent devices with sub-second latency.

### 🚇 2. Zero-Configuration Public Cloudflare Tunnel
- **Global Reachability**: Seamless connection between parents, children, and bystanders across disparate mobile carrier networks (4G/5G) and Wi-Fi networks without requiring router port-forwarding or public IP addresses.
- **Windows Automation Scripts**:
  - `start-backend.bat`: Verifies JDK 21, compiles backend, starts Spring Boot, polls health endpoint, auto-downloads `cloudflared.exe` if missing, starts public HTTPS tunnel, and prints the live public URL.
  - `backend-status.bat`: Live health check of backend (PID/port) and tunnel daemon.
  - `stop-backend.bat`: Graceful cleanup of backend and tunnel background tasks.

### 🔑 3. Google Authentication & JWT Security
- **OAuth2 Token Verification**: `GoogleTokenVerifierService` cryptographically validates Google ID tokens against Google public RSA certificates for verified parent sign-in.
- **Stateless Bearer JWTs**: Signs and validates HMAC-SHA256 access tokens (1-hour expiry) and rotating refresh tokens (30-day expiry).
- **Development Login Endpoint**: Public `/api/v1/auth/dev-login` endpoint for rapid offline testing without Google Play Services.

### 📍 4. Continuous 1-Minute Child Location Tracking
- **Foreground Location Service (`ChildLocationForegroundService`)**: High-accuracy periodic GPS tracking service operating in child mode, uploading coordinates every 60 seconds with offline breadcrumb buffer.
- **Parent Settings Configuration**: `SettingsScreen` includes interactive "Backend & Cloud Tunnel" settings card allowing live URL entry and ping verification.

### 🛡️ 5. GPIO 3 / GPIO 4 Hardware Tamper Loop Stabilization
- **Physical Strap Continuity**: Dynamic continuity pulse settling window increased to 200µs (`delayMicroseconds(200)`) in ESP32-S3 firmware (`tamper_monitor.cpp`), eliminating false open-circuit cut detections caused by ribbon line capacitance on internal pull-downs.
- **Android BLE Parsing**: Added `alertType` field propagation in `BleSafetyManager.kt` and mapped `AlertFlag.TAMPER` in `SafeBandViewModel.kt` to trigger database updates and instant parent alerts.

### 🛰️ 6. Emergency-Only Community Relay Mesh Network
- **Zero Child PII**: Anonymous bystander scanning ignores routine heartbeats and ingests only `ALERT_SOS` and `ALERT_TAMPER` beacons.
- **Observer Privacy**: Ephemeral observer tokens prevent reverse tracking of helpful bystanders.

### 📦 7. Verified Build Artifacts

| Component | File Path | File Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android App (Release)** | `app/build/outputs/apk/release/app-release.apk` | 14,180,754 bytes (13.5 MB) | `3A7722791167CFCF3D3208B2EA462725299D9374E11AADEAE6C79E1E0D136618` |
| **Android App (Debug)** | `app/build/outputs/apk/debug/app-debug.apk` | 21,586,028 bytes (20.6 MB) | `6FE878F95D748DDC9C54826FD58EB2FC1E15E86D81E57CE5B034E2E586AE30C6` |
| **Spring Boot Backend** | `backend/build/libs/backend-4.0.0.jar` | 121,453,344 bytes (115.8 MB) | `3A768627DD724EF77126DC11C6A78EB0F51B993D8AF1641030DFF124F1E740D2` |
| **ESP32-S3 Firmware** | `hardware/esp32-child-node/.pio/build/esp32-s3-devkitc-1/firmware.bin` | 944,208 bytes (922 KB) | `3E8D822709A88941C7940E3FFD09DECD1299278815C7B2BB079DC35E5A1647DF` |

---

# SafeBand Guardian v3.6.1 Release Notes

**Release Version:** v3.6.1 (Stabilization & Quality Hardening Patch)  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 7.0 (API 24) through Android 15 (API 36)  
**Hardware Node:** ESP32-S3 BLE Wearable Node  
**Release Date:** November 2026  
**Git Tag:** `v3.6.1`  

---

## 🛠️ What's New & Stabilized in v3.6.1: Safety Hardening & Visual QA Evidence

### 🚨 1. Safety-Critical Fix: Accidental SOS Cancellation Protection
- **Root Cause Remediation:** In v3.6.0, an active SOS emergency broadcast could be cancelled by an inadvertent single tap on the 150dp central panic button or the "Cancel Siren" secondary button on `ChildHomeScreen.kt`. This violated child safety ergonomics during panics, struggles, or pocket friction.
- **Remediation:** Added `showCancelSosDialog` with a mandatory confirmation modal dialog (`AlertDialog`). Tapping the button now presents a clear dialog:
  - *"Cancel Emergency Alert? Are you sure you want to cancel the emergency alert? Parents, emergency contacts, and community relays will be notified that this alert has ended."*
  - **"Keep Alert Active"**: Safe primary action dismissing the dialog while maintaining active beacon transmission.
  - **"Yes, Cancel Alert"**: Deliberate action confirming resolution and cancelling alarms.
- **Verification:** Unit tested in `ScreensVisualScreenshotTest.test_sos_cancellation_requires_confirmation_dialog` with 100% pass.

### 📸 2. Automated Visual Regression Suite & Real PNG Screenshot Artifacts
- **Roborazzi Native Graphics Integration:** Implemented automated screenshot test suite (`ScreensVisualScreenshotTest.kt`) using `@GraphicsMode(GraphicsMode.Mode.NATIVE)` on Google Pixel 8 form factor (`RobolectricDeviceQualifiers.Pixel8`, Android SDK 36).
- **14 High-Resolution Screenshots Captured:** Generated 14 real PNG screenshots into `app/src/test/screenshots/` (1.8 MB total):
  1. `01_mode_selection_screen.png`
  2. `02_first_launch_screen.png`
  3. `03_child_home_normal.png`
  4. `04_parent_home_dashboard.png`
  5. `05_child_provisioning_screen.png`
  6. `06_settings_screen.png`
  7. `07_child_settings_screen.png`
  8. `08_alert_history_screen.png`
  9. `09_trusted_contacts_screen.png`
  10. `10_safe_zone_config_screen.png`
  11. `11_emergency_alert_sheet.png`
  12. `12_relay_emergency_alert_sheet.png`
  13. `13_permission_rationale_dialog.png`
  14. `14_child_home_emergency.png`
- Every screenshot artifact is cataloged and hashed in `UI_VISUAL_QA_EVIDENCE_V3_6_0.md`.

### 🧪 3. Expanded Test Suite: 144 / 144 Tests Passed
- Test coverage expanded from 129 tests in v3.6.0 to **144 tests in v3.6.1** (15 new automated visual and UI interaction tests).
- 100% pass rate across all 22 test suites (`./gradlew testDebugUnitTest`).

### 🔒 4. Security & Privacy Audit Verification
- Formally audited Android Keystore AES-256-GCM authenticated encryption (`SafeBandSecurityManager.kt`), 15-minute ephemeral ID rotation, Zero-PII BLE advertisement contract (`0x5AFE`), bystander relay isolation, and MAC address Logcat scrubbing. Documented in `SECURITY_PRIVACY_AUDIT_V3_6_0.md`.

### ⚙️ 5. ESP32-S3 Firmware Byte-Level Compatibility Audit
- Verified byte-level synchronization between ESP32-S3 firmware (`safeband_ble.cpp`) and Android BLE parser (`BleSafetyManager.kt`).
- GPIO 3 (TX) and GPIO 4 (RX) dynamic 2-phase continuity tamper detection verified. Firmware compiled cleanly with PlatformIO (944 KB binary). Documented in `FIRMWARE_COMPATIBILITY_AUDIT_V3_6_0.md`.

---

# SafeBand Guardian v3.6.0 Release Notes

**Release Version:** v3.6.0  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 7.0 (API 24) through Android 15 (API 36)  
**Hardware Node:** ESP32-S3 BLE Wearable Node  
**Release Date:** November 2026  
**Git Tag:** `v3.6.0`  

---

## 🎨 What's New & Refined in v3.6.0: Complete UI/UX Overhaul

### 💎 1. Universal Design System Architecture
- **Material 3 Design Tokens:** Implemented modern, accessible design tokens across `SafeBandColor.kt`, `SafeBandTypography.kt`, `SafeBandShapes.kt`, and `SafeBandSpacing.kt`.
- **Calm, Human-Centered Aesthetics:** Deprecated the harsh, high-density "Obsidian Telemetry" terminal styling (monospaced terminal fonts, mechanical toggles, 0dp margins, wireframes). Replaced with empathetic Slate neutrals, SafeBand Blue 600 (`#2563EB`), Indigo 600 (`#4F46E5`), and semantic alerts (`#10B981` Emerald, `#F59E0B` Amber, `#EF4444` Rose).
- **Reusable Component Library (`SafeBandComponents.kt`):** Universal components with ColumnScope layout support: `SafeBandCard`, `SafeBandHeroCard`, `SafeBandPrimaryButton`, `SafeBandSecondaryButton`, `SafeBandStatusBadge`, `SafeBandSectionHeader`, `SafeBandAvatar`, `SafeBandMetricChip`, `SafeBandTextField`, and `SafeBandEmptyState`.

### 🛡️ 2. Screen-by-Screen Visual & Usability Transformation
- **Parent Experience (`ParentHomeScreen.kt`):** Branded top app bar, modern bottom navigation bar (`Monitor`, `Safe Zones`, `History`, `Contacts`), paired children carousel with dynamic device-health badges (`CONNECTED`, `STALE`, `OFFLINE`), child safety hero card, and quick action dock (`Add Child`, `Test Siren`, `Maintenance Mode`, `Configure Zones`).
- **Child Experience (`ChildHomeScreen.kt`):** Calming, reassuring hero banner (*"You Are Protected — SafeBand is watching over you"*), 2-second hold circular SOS button with animated progress ring and haptic feedback to prevent accidental triggers, quick calling buttons for National Emergency (112) and Child Helpline (1098), and emergency countdown cancel modal.
- **Guardian Access Guard (`ChildSettingsScreen.kt`):** Smooth 64dp circular number keys with pulse feedback and shake animation on invalid PIN entry, child-proofing device settings.
- **Grouped Settings (`SettingsScreen.kt`):** Logical grouped card architecture separating Security, Network/Endpoints, Pairing Challenge generation, Medical Bio-data, and Wearable Diagnostics.
- **Incident Timeline (`AlertHistoryScreen.kt`):** Card-based chronological events replacing raw terminal logs, interactive category filter chips (`All`, `SOS`, `Tamper`, `Geofence`), and seamless incident replay modal launcher.
- **Trusted Contacts (`TrustedContactsScreen.kt`):** Priority cards with color-coded initials avatars, one-tap phone calling, emergency SMS dispatch, and a clean contact creation dialog.
- **Safe Zones & Corridors (`SafeZoneConfigScreen.kt` & `SafeZoneCanvas.kt`):** Anti-aliased radar canvas visualizer with preset safe zones (`Home`, `School`, `Park`) and trusted route safety corridor controls.
- **Community Relay Sheet (`RelayEmergencyAlertSheet.kt`):** Good Samaritan translucent bottom sheet with clear zero-PII privacy reassurance and anonymous location telemetry.
- **Parent Emergency Alarm Sheet (`EmergencyAlertSheet.kt`):** High-priority pulsing alarm sheet with child dossier, elapsed timer, and immediate 112 calling shortcuts.
- **Provisioning Wizard (`ChildProvisioningScreen.kt`):** 4-step guided onboarding wizard with pulsing radar scanner and encrypted GATT transfer progress.

### ♿ 3. Accessibility & Usability Hardening
- **WCAG 2.1 Compliance:** Body text exceeds 4.5:1 contrast ratio; primary headings and emergency badges exceed 7.0:1 (AAA compliance).
- **Minimum Touch Targets:** All interactive buttons, chips, and icons strictly conform to $\ge 48\times 48\text{ dp}$.
- **Dynamic Font Scaling:** UI layouts flexibly accommodate user-scaled system fonts up to 200% without clipping.

### 📋 4. Documentation Deliverables
- `UI_UX_AUDIT_V3_6_0.md`: Full aesthetic, structural, and usability audit of the pre-v3.6.0 interface.
- `UI_SCREEN_COVERAGE_V3_6_0.md`: Inventory and progress checklist across all 14 screens and overlays.
- `UI_DESIGN_SYSTEM_V3_6_0.md`: Complete design system token reference and component architectural specification.
- `UI_VISUAL_QA_V3_6_0.md`: Visual inspection and accessibility contrast ratio report.
- `UI_REGRESSION_TESTS_V3_6_0.md`: Regression verification report.

---

## 📦 Verified Build Artifacts

| Component | Path | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android Release APK** | `app/build/outputs/apk/release/app-release.apk` | **14.1 MB** (14,147,946 B) | `A38E5F2FD0B94E3D868DA13FEB08BAC825A103B1F2DA5660A959FBB015FDFEEA` |
| **Android Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | **21.0 MB** (20,951,972 B) | `CF4F53D3850B7C6C4E7406642B304CD5A655B8FC6C5E77A79324DB3CC1AB993C` |
| **ESP32-S3 Firmware** | `hardware/esp32-child-node/.pio/build/esp32-s3-devkitc-1/firmware.bin` | **922 KB** (944,208 B) | `9BBF7B8C247EDFE0BCFA78DE247678BDAFA61579D54A9E9E4915D56348076D0E` |

---

## 🧪 Automated Verification Summary

- **Unit Tests:** 129 of 129 passed (`./gradlew testDebugUnitTest` - 100% success rate)
- **Android Lint:** 0 errors, clean analysis (`./gradlew lintDebug` passed)
- **ESP32-S3 Build:** PlatformIO build completed successfully (`pio run -d hardware/esp32-child-node`)
- **Git Release Tag:** `v3.6.0`

---

# SafeBand Guardian v3.5.0 Release Notes

**Release Version:** v3.5.0  
**Package:** `com.aistudio.safeband.xqmt`  
**Target Platform:** Android 7.0 (API 24) through Android 15 (API 36)  
**Hardware Node:** ESP32-S3 BLE Wearable Node  
**Release Date:** November 2026  
**Git Tag:** `v3.5.0`  

---

## 🚀 What's New & Hardened in v3.5.0

### 🔐 1. Security & Cryptographic Hardening
- **Android Keystore Credential Encryption:** Introduced `SafeBandSecurityManager.kt` providing hardware-backed AES-256-GCM encryption with 12-byte random IVs and 128-bit authentication tags. Parent node auth tokens are automatically encrypted before storing to `DataStore` at rest.
- **Cryptographically Authenticated GATT Provisioning:** Provisioning payload includes timestamp, monotonic version, and HMAC-SHA256 signature calculated over all fields (`PROV:...|version|timestamp|hmac`). Wearables reject replayed packets and stale versions.
- **Authenticated & Replay-Protected Strap Maintenance:** Maintenance authorizations use strictly monotonic sequence numbers and HMAC-SHA256 signatures (`MAINT:AUTH:dur:token:seq:hmac`). Wearables reject commands with sequence $\le s\_lastMaintenanceSeq$.
- **Fail-Secure Boot Policy:** ESP32-S3 clears any active maintenance duration (`s_tamperAuthorizedUntil = 0`) on boot/reboot, preventing attackers from using power-cycles to bypass strap continuity alarms.
- **Zero-PII Community Relay Mesh:** Public relay payloads and BLE advertisements contain strictly zero child PII (no names, medical records, or parent phone numbers). Public tracking is limited to ephemeral IDs.

### 🌐 2. Community Emergency Relay Reliability
- **Honest Location Telemetry:** Eliminated all hardcoded fake GPS coordinates (`37.7749, -122.4194`). When GPS fixes or location permissions are absent, the relay transmits honest `null` coordinates, protecting search-and-rescue integrity.
- **Background Store-and-Forward Queue Draining:** Added `drainForwardingQueue()` with exponential backoff ($10s \times 2^{\text{attempts}}$ capped at 5m) and a 24-hour expiration policy (`INCIDENT_EXPIRY_MS = 86400000L`).
- **Network State Restoration Callback:** Automatically triggers queue draining via `ConnectivityManager.NetworkCallback` when internet connectivity is re-established.
- **Intelligent Location Enrichment:** Late-arriving GPS fixes enrich existing pending forwarding records without creating duplicate incidents.

### ⚡ 3. End-to-End Alert Type & Health Integration
- **BLE Advertisement Byte 24 Encoding:** Telemetry packets encode alert types directly in byte 24 of the manufacturer payload (`0x00` NORMAL, `0x01/0x03` SOS, `0x02/0x04` TAMPER), decoded by `BleSafetyManager` and routed to emergency responders.
- **Tri-State Dynamic Device Health Model:** Parent dashboard child cards display real-time connection status (`CONNECTED` < 30s, `STALE` 30s-5m, `OFFLINE` > 5m) with formatted elapsed age ("2s ago", "1m ago").

### 📋 4. Documentation & Validation Deliverables
- `V3_5_0_TECHNICAL_AUDIT.md`: Baseline technical audit of v3.4.0.
- `V3_5_0_IMPLEMENTATION_TRACKER.md`: Granular work tracking across all phases.
- `FAILURE_RECOVERY_MATRIX.md`: Comprehensive recovery matrix across Wearable, Android, and Communication layers.
- `HARDWARE_VALIDATION_V3_5_0.md`: Complete physical hardware testing procedure with wiring, serial logs, and acceptance criteria.
- `SECURITY_REVIEW_V3_5_0.md`: Threat model, cryptographic audit, and privacy verification.

---

## 📦 Verified Build Artifacts

| Component | Path | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android Release APK** | `app/build/outputs/apk/release/app-release.apk` | **14.1 MB** | `9B4950C3E7F227BE8912126D8D5E862B3914AFD08BC6D0E0565E38D0E21FA531` |
| **Android Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | **21.5 MB** | `05CD209B19BF78BF89F42C1EE26749C3A6AD693FAD9A271A4E5C24407EDEE726` |
| **ESP32-S3 Firmware** | `hardware/esp32-child-node/.pio/build/esp32-s3-devkitc-1/firmware.bin` | **922 KB** | `9BBF7B8C247EDFE0BCFA78DE247678BDAFA61579D54A9E9E4915D56348076D0E` |

---

## 🧪 Automated Verification Summary

- **Unit Tests:** 129 of 129 passed (`./gradlew testDebugUnitTest` - 100% success rate in 27.8s)
- **Android Lint:** 0 errors, clean analysis (`./gradlew lintDebug` passed in 1m 40s)
- **ESP32-S3 Build:** PlatformIO build completed successfully (`pio run -d hardware/esp32-child-node`)
- **Git Release Tag:** `v3.5.0`

---

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
