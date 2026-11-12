# SAFEBAND GUARDIAN — V3.3 BASELINE AUDIT REPORT
**Date:** November 2026  
**Auditor:** Senior Android Architect & Security Engineer  
**Baseline Version:** v3.2.0 (Build Code 9)  
**Target Milestone:** v3.3.0 (Core Architecture, Role Separation, Google Authentication & Feature Refinement)

---

## 1. Executive Summary & Baseline State

SafeBand Guardian is an offline-first, peer-to-peer child safety Android application designed to operate over Bluetooth Low Energy (BLE `0x5AFE`), on-device motion anomaly detection, geofencing, and multi-tier risk evaluation.

While v3.2.0 achieved reliable background alert delivery (`connectedDevice|location` foreground service + screen wake lock + full-screen intent) and Frosted Glass monochrome visual styling, **it lacks internal role separation, user authentication, and secure device provisioning**. Specifically:
- **No Parent Authentication:** There is currently no Google Sign-In or user identity mechanism.
- **Unrestricted Mode Toggle:** `AppMode` (`UNSELECTED`, `CHILD`, `PARENT`) is stored as a simple string in `SafeBandPreferences` (`DataStore`). Any user can switch roles freely in `SettingsScreen` or `ModeSelectionScreen`.
- **No Child Provisioning Security:** Adding an ESP32-S3 band or child phone consists of typing a device ID string without cryptographic pairing challenges, parent signatures, or relationship authorization.
- **Single-Activity Architecture with Flat Screen Switching:** `MainActivity` renders all screens inside a single `AnimatedContent` based on a flat `AppDestination` enum, meaning child and parent screens share the same activity context with no authorization barriers.

---

## 2. Git & Build Baseline Verification

| Attribute | Baseline Status | Notes |
|---|---|---|
| **Branch** | `main` | Synchronized with `origin/main` |
| **Commit** | `bbf0cf9` | `feat(release): SafeBand Guardian v3.2.0` |
| **Uncommitted Changes** | `ASIP_Project_Diary_Completed_2026-27.docx` | Untouched binary documentation preserved |
| **Gradle Test Result** | **100% PASS** (96/96 Unit Tests) | `./gradlew testDebugUnitTest` passed in 7s |
| **Compilation Status** | **0 Errors, Clean Build** | Compile SDK 36, Target SDK 36, Min SDK 24 |
| **Room Database Version** | **Schema v4** | 15 Entities, Migrations: `1_2`, `2_3`, `3_4` |

---

## 3. Feature Inventory & Current Implementation Matrix

| Feature | Screen / Composable | Key Implementing Classes | Current Functionality | Deficiencies & Gaps | Target Improvements (v3.3) | Tests |
|---|---|---|---|---|---|---|
| **Mode Entry & Selection** | `ModeSelectionScreen.kt` | `SafeBandViewModel`, `SafeBandPreferences` | Displays 2 cards: "Child Device" and "Parent / Guardian Scanner". Clicking immediately updates `appMode` in DataStore. | **Critical:** No auth required. Anyone can tap Parent or Child. | Replace with onboarding: "Set up as Parent" (Google Auth) vs "Set up as Child" (Pairing Code). | `ModeSelectionTest` |
| **Parent Dashboard** | `ParentHomeScreen.kt` | `ParentHomeScreen.kt`, `SafeBandViewModel` | Displays paired children carousel, ESP32 status, RSSI proximity, displacement meter, timeline. | No auth check; mixes telemetry with management actions; lacks proper empty states when 0 children paired. | Re-architect into modular Parent experience; show family overview, active journeys, and school status. | `ParentHomeScreenTest` |
| **Child Dashboard** | `ChildHomeScreen.kt` | `ChildHomeScreen.kt`, `SafeBandViewModel` | Displays circular SOS dial (2s hold), quick action buttons, safe zone displacement, diagnostics drawer. | Allows access to `SettingsScreen` which exposes role switching to Parent mode. | Sandbox Child Mode completely; strip access to Parent settings; child-friendly safety reassurance. | `ChildHomeScreenTest` |
| **Device Pairing** | `ParentHomeScreen.kt` (Dialog) | `SafeBandViewModel`, `PairedChildDao` | Manual text entry dialog (`deviceId`, `childName`, `age`, `bloodType`, `phone`). | No secure pairing challenge; child device cannot verify parent authority; no one-time pairing code. | Implement parent-generated 6-digit one-time pairing challenge with TTL for child device provisioning. | `DeviceProvisioningTest` |
| **BLE Communication** | `BleSafetyManager.kt`, `BleSafetyMonitorService.kt` | `BleSafetyManager`, `BleSafetyMonitorService` | Interleaved advertising rotation (1200ms) for `Telemetry`, `BioCore`, `BioPhone`. Scanning in foreground service. | Well-implemented in v3.2.0; needs cleaner separation between Child advertiser and Parent scanner roles. | Isolate advertising pipeline to Child role and scanning/monitoring to Parent role. | `PrivacyBleTest`, `HardwareSafetyAndBackgroundAlertTest` |
| **Safe Zones & Geofencing** | `SafeZoneMapView.kt`, `SafeZoneConfigScreen.kt` | `LocationSafetyHelper`, `SafeBandPreferences` | Leaflet/OSM WebView map with geofence circle, coordinate display, reverse geocoding address resolution. | Stored in preferences rather than Room table; child mode could theoretically modify safe zone if settings opened. | Enforce Parent-only access; bind safe zones to specific child profiles in Room DB. | `GeofenceAndLocationConfidenceTest` |
| **School Safety** | `SchoolManagementScreen.kt` | `SchoolSafetyManager`, `SchoolDao` | Configure school profile, lat/lon radius, arrival/departure schedules, exception days. | Functional in Room v4; needs child profile foreign key association. | Link schools to specific children; integrate with Parent dashboard cards. | `NewSafetyFeaturesTest` |
| **Journey Safety** | `JourneySafetyScreen.kt` | `JourneySafetyManager`, `JourneyDao` | Configure journey name, destination, corridor radius, departure/arrival timestamps, corridor deviation. | Functional in Room v4; lacks active journey summary on Parent home. | Surface active journeys prominently on Parent dashboard; show journey status on Child screen. | `NewSafetyFeaturesTest` |
| **SOS & Incidents** | `ChildHomeScreen.kt`, `EmergencyAlertSheet.kt` | `BleSafetyManager`, `BleSafetyMonitorService`, `RiskEngine` | 2s hold SOS dial, 10s false-alarm countdown, instant BLE 10Hz burst, lockscreen full-screen notification. | State names in UI sometimes technical (`CONFIRMATION_RESOLVED_REENTRY`). | Formalize strict incident lifecycle (`CREATED`, `BROADCASTING`, `RECEIVED`, `ACKNOWLEDGED`, `RESOLVED`). | `RiskEngineV2Test`, `IncidentAndTimelineTest` |
| **Guardian Management** | `TrustedContactsScreen.kt` | `SafetyRepository`, `TrustedContactDao` | Add contact name, phone, relationship. | Flat list; no invitation status or role permissions (e.g. view-only vs pickup authorized). | Upgrade to structured Guardian entities with roles (`PRIMARY_PARENT`, `TRUSTED_GUARDIAN`, `EMERGENCY_CONTACT`). | `MultiChildAndProfileIsolationTest` |
| **Pickup & Handover** | `PickupManagementScreen.kt` | `PickupHandoverDao`, `SafetyRepository` | Generate 6-char verification token, approval/rejection audit trail, expired pass detection. | Solid Room v4 table; needs integration with Google authenticated parent identity. | Tie `parentSignature` to authenticated parent user ID. | `NewSafetyFeaturesTest` |
| **Settings & Role Switch** | `SettingsScreen.kt` | `SafeBandViewModel`, `SafeBandPreferences` | Toggle safety monitoring, switch role button, edit child bio, custom device ID. | **Critical Security Flaw:** Unrestricted role switching allows child to switch to Parent mode and alter settings. | Remove unrestricted mode switch; require Google re-auth or PIN to exit Child mode / access Parent settings. | `RoleIsolationTest` |

---

## 4. Architectural Analysis & Proposed v3.3 Evolution

### 4.1 Dependency & Package Evolution
Rather than violently moving all 40+ existing files at once, we will organize the codebase cleanly into:
```
com.example/
├── core/
│   ├── auth/                  # AuthRepository, GoogleCredentialManager, AuthenticatedUser, SessionManager
│   ├── rbac/                  # AppRole, Permission, AccessController, AuthorizationException
│   ├── ble/                   # BleSafetyManager, BleSafetyMonitorService, PacketDecoders
│   ├── safety/                # RiskEngine, MotionAnomalyDetector, HardwareSafetyEnforcer
│   └── location/              # LocationSafetyHelper, LocationCache, LocationAddressResolver
├── data/
│   ├── local/                 # Room Database (Schema v5), DAOs, Entities
│   ├── preferences/           # DataStore (Auth tokens, preferences)
│   └── repository/            # SafetyRepository, AuthRepository
├── features/
│   ├── onboarding/            # FirstLaunchScreen, ParentOnboardingScreen, ChildProvisioningScreen
│   ├── parent/                # ParentNavigation, ParentDashboard, ChildrenManager, Geofence, School, Journey, Settings
│   └── child/                 # ChildNavigation, ChildDashboard, SosDial, ChildReassurance
└── ui/
    ├── components/            # GlassCard, GlassButton, HardwarePrerequisiteBanner, SafeZoneMapView
    └── theme/                 # Frosted Glass Monochrome tokens (Color, Theme, Type)
```

### 4.2 Database Evolution (Schema v4 $\to$ Schema v5)
The current database is at version 4. In v3.3, we will add:
1. `parent_accounts` (internal `userId`, `googleSub`, `email`, `displayName`, `photoUrl`, `createdAt`, `authStatus`)
2. `provisioning_challenges` (`challengeId`, `childDeviceId`, `oneTimeCode`, `expiresAt`, `status`, `parentUserId`)
3. `family_guardians` (`guardianId`, `parentUserId`, `name`, `phoneNumber`, `relationship`, `role`, `status`)
4. Safe Room migration `MIGRATION_4_5` preserving 100% of existing `safety_events`, `paired_children`, `pickup_authorizations`, `supervised_journeys`, etc.

---

## 5. Implementation Roadmap (Phases 1 through 10)

1. **Step 2:** Architecture & Core Preparation (`core/auth`, `core/rbac`, SessionManager, Google Credential Manager integration).
2. **Step 3:** Database Migration `MIGRATION_4_5` & Domain Repositories.
3. **Step 4:** Onboarding Redesign (First launch role separation: Parent Google Sign-in vs Child Provisioning).
4. **Step 5:** Complete Parent/Child Experience Separation (Dedicated navigation, restricted Child Mode, Google-authenticated Parent Dashboard).
5. **Step 6:** Parent-Controlled Child Provisioning Flow (6-digit one-time code with TTL).
6. **Step 7:** Existing Feature Refinement (Dashboard, Safe Zones, School, Journeys, SOS, Guardians, Pickup).
7. **Step 8:** Exhaustive Testing (Unit, RBAC isolation, Database migration, Auth mock).
8. **Step 9:** Verification, Build Debug APK, and Final Report `docs/V3_3_IMPLEMENTATION_REPORT.md`.

