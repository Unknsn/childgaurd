# SafeBand Guardian v4.0.0 — Requirement Traceability Matrix

| Req ID | Requirement Description | Implementation Component / File | Verification Method | Current QA Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-01** | Unified single APK containing Parent & Child modes | `app/src/main/java/com/example/ui/SafeBandApp.kt` | Robolectric & Compose UI Tests | **PASS** |
| **REQ-02** | Google OAuth2 ID Token Verification | `backend/.../security/GoogleTokenVerifierService.kt` | Mock token verification test | **PASS** |
| **REQ-03** | Public Internet Access via Secure Cloudflare Tunnel | `start-backend.ps1` & `PUBLIC_TUNNEL_SETUP.md` | Health check polling over tunnel | **PASS** |
| **REQ-04** | 1-Minute Child Location Tracking | `app/.../service/ChildLocationForegroundService.kt` | Android test suite & Service test | **PASS** |
| **REQ-05** | Parent Remote Location Telemetry Refresh | `app/.../SafeBandViewModel.kt` & `SafeBandApiClient.kt` | Unit tests & backend query test | **PASS** |
| **REQ-06** | Dual-Layer Geofence Breach Detection | `backend/.../service/Services.kt` (`LocationService`) | Haversine unit tests & integration test | **PASS** |
| **REQ-07** | ESP32 GPIO 3/4 Strap Tamper Detection | `hardware/esp32-child-node/src/tamper_monitor.cpp` | PlatformIO build & logic tests | **SIMULATED PASS** (Physical bench pending) |
| **REQ-08** | Physical Tamper Alert Propagation in BLE Parser | `app/.../service/BleSafetyManager.kt` | `BleSafetyManagerTest.kt` | **PASS** |
| **REQ-09** | Emergency-Only Community Relay Mesh (Zero PII) | `app/.../service/EmergencyRelayManager.kt` & `RelayController.kt` | Backend API integration tests | **PASS** |
| **REQ-10** | Spring Boot Dual-Mode Persistence (H2 & Postgres) | `backend/src/main/resources/application.yml` | Flyway V1 schema migration test | **PASS** |
| **REQ-11** | Real-Time Incident Escalation via WebSockets | `backend/.../config/WebSocketConfig.kt` | WebSocket handshake integration test | **PASS** |
| **REQ-12** | Material 3 Responsive UI & Button Letter-Wrap Fix | `app/.../ui/components/SafeBandComponents.kt` | 14 Roborazzi screenshot tests | **PASS** |
| **REQ-13** | Safe Zone Map Auto-Centering Without Drift | `app/.../ui/screens/SafeZoneConfigScreen.kt` | Roborazzi visual regression test | **PASS** |
| **REQ-14** | Secure BLE Challenge-Response Hardware Pairing | `backend/.../controller/Controllers.kt` (`PairingController`) | Pairing integration pipeline test | **PASS** |
| **REQ-15** | Windows Developer Startup & Shutdown Automation | `start-backend.bat` & `stop-backend.bat` | Windows script execution test | **PASS** |
| **REQ-16** | Comprehensive Audit & Security Logging | `backend/.../service/Services.kt` (`AuditService`) | Audit log integration verification | **PASS** |
| **REQ-17** | SOS Confirmation Dialog on Accidental Tap | `app/.../ui/screens/ChildHomeScreen.kt` | `ScreensVisualScreenshotTest.kt` | **PASS** |
| **REQ-18** | Automated Build Artifact & SHA-256 Checksumming | Root build workflow & Gradle tasks | Build execution & Get-FileHash | **PASS** |
