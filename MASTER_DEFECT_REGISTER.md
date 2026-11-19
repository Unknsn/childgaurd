# SafeBand Guardian v4.0.0 — Master Defect Register

## 1. Defect Classification & Severity Framework

| Severity | Definition | Policy |
| :--- | :--- | :--- |
| **CRITICAL** | Security breaches, dropped SOS/Tamper panics, unauthorized child access, data loss. | Must fix 100% prior to certification. |
| **HIGH** | Broken pairing, broken location tracking, failed backend synchronization, geofence failure. | Must fix 100% prior to certification. |
| **MEDIUM** | UI rendering defects, unhandled network edge cases, button wrapping, reconnection lag. | Address or explicitly document. |
| **LOW** | Cosmetic inconsistencies, non-blocking log warnings, minor layout paddings. | Address wherever feasible. |

---

## 2. Master Defect Ledger

### DEF-001 [SEVERITY: HIGH] — Community Relay Forwarding Endpoint Misalignment
* **Affected Module**: `app/src/main/java/com/example/service/EmergencyRelayManager.kt`
* **Reproduction Steps**:
  1. Community bystander Android device detects an `ALERT_SOS` or `ALERT_TAMPER` beacon.
  2. Device attempts to forward sighting to configured backend.
  3. `EmergencyRelayManager.kt` dispatched to `http://$parentIp:$parentPort/api/relay/alert`.
* **Expected Behavior**: Bystander sighting forwards to `${backendBaseUrl}/api/v1/relay/observations` and correctly notifies registered guardians.
* **Actual Behavior**: Dispatched to legacy endpoint with legacy JSON schema, failing to reach the Spring Boot backend cloud.
* **Root Cause**: `EmergencyRelayManager.kt` was not updated to read `preferences.getBackendBaseUrlDirect()` and lacked the v4.0.0 `RelayObservationRequest` schema.
* **Files Modified**:
  * `app/src/main/java/com/example/service/EmergencyRelayManager.kt`
  * `backend/src/main/kotlin/com/safeband/backend/controller/Controllers.kt`
  * `backend/src/main/kotlin/com/safeband/backend/dto/Dtos.kt`
  * `backend/src/main/kotlin/com/safeband/backend/service/Services.kt`
* **Fix Implemented**:
  1. Updated `EmergencyRelayManager.kt` to target `$baseUrl/api/v1/relay/observations` when `backendBaseUrl` is configured.
  2. Combined DTO payload containing both v4.0.0 and legacy keys for zero-loss forward compatibility.
  3. Added `/api/relay/alert` alias in `RelayController.kt` and updated `RelayService.recordObservation` to dynamically resolve either `incidentId` or `broadcastId`/`deviceId`.
* **Status**: **RESOLVED & VERIFIED**.

---

### DEF-002 [SEVERITY: HIGH] — Missing Cloud Incident Synchronization on Manual SOS & Tamper Beacons
* **Affected Module**: `app/src/main/java/com/example/ui/SafeBandViewModel.kt`
* **Reproduction Steps**:
  1. Child taps Manual SOS button on `ChildHomeScreen`, or ESP32 reports physical strap cut.
  2. Local Room event log is created and BLE broadcast starts.
  3. Remote parent phone connected via cellular tunnel is checked.
* **Expected Behavior**: Incident is immediately dispatched via HTTPS to backend `POST /api/v1/incidents` and pushed to remote parents via WebSockets.
* **Actual Behavior**: Incident only existed in local device database and BLE advertisement, without uploading to cloud backend.
* **Root Cause**: `SafeBandViewModel.triggerManualSos()` and `processBeaconPayload()` omitted calls to `apiClient.reportEmergencyIncident()`.
* **Files Modified**:
  * `app/src/main/java/com/example/ui/SafeBandViewModel.kt`
* **Fix Implemented**:
  1. In `triggerManualSos()`, added asynchronous dispatch to `apiClient.reportEmergencyIncident(..., "MANUAL_SOS", "CRITICAL")`.
  2. In `processBeaconPayload()`, added asynchronous dispatch to `apiClient.reportEmergencyIncident(..., if (isTamper) "TAMPER" else "MANUAL_SOS", "CRITICAL")`.
* **Status**: **RESOLVED & VERIFIED**.

---

### DEF-003 [SEVERITY: HIGH] — Remote Child Location Not Auto-Updating on Parent Dashboard
* **Affected Module**: `app/src/main/java/com/example/network/SafeBandApiClient.kt` & `SafeBandViewModel.kt`
* **Reproduction Steps**:
  1. Child device foreground service uploads periodic 1-minute GPS fixes to backend.
  2. Parent phone views `ParentHomeScreen` or `SafeZoneConfigScreen`.
* **Expected Behavior**: Parent map and telemetry cards automatically display the child's remote GPS location received from backend.
* **Actual Behavior**: Parent app only displayed local device GPS coordinates, lacking remote child telemetry polling.
* **Root Cause**: `SafeBandApiClient` lacked `getLatestChildLocation()` and `SafeBandViewModel` had no remote location state flow or periodic refresh loop.
* **Files Modified**:
  * `app/src/main/java/com/example/network/SafeBandApiClient.kt`
  * `app/src/main/java/com/example/ui/SafeBandViewModel.kt`
  * `app/src/main/java/com/example/ui/screens/ParentHomeScreen.kt`
  * `app/src/main/java/com/example/ui/screens/SafeZoneConfigScreen.kt`
* **Fix Implemented**:
  1. Added `getLatestChildLocation(childId)` in `SafeBandApiClient.kt` targeting `GET /api/v1/locations/children/{childId}/latest`.
  2. Added `_remoteChildLocation` and `refreshRemoteChildLocation()` in `SafeBandViewModel.kt`, with automatic 30s background polling in Parent Mode.
  3. Updated `ParentHomeScreen.kt` and `SafeZoneConfigScreen.kt` to bind to `remoteChildLocation`.
* **Status**: **RESOLVED & VERIFIED**.

---

### DEF-004 [SEVERITY: MEDIUM] — Automatic Server-Side Geofence Breach Detection
* **Affected Module**: `backend/src/main/kotlin/com/safeband/backend/service/Services.kt`
* **Reproduction Steps**:
  1. Child uploads GPS breadcrumbs located outside their configured safe zone radius.
  2. Backend stores coordinates in `locations` table.
* **Expected Behavior**: Backend automatically calculates Haversine distance against active geofence zones, creates a `GEOFENCE_EXIT` incident, and alerts the parent via WebSocket.
* **Actual Behavior**: Backend saved the location and broadcasted coordinates, but required client-side exit reporting to register an incident.
* **Root Cause**: `LocationService.ingestLocation()` did not evaluate `geofenceZoneRepository.findByChildIdAndIsActiveTrue()`.
* **Files Modified**:
  * `backend/src/main/kotlin/com/safeband/backend/service/Services.kt`
* **Fix Implemented**:
  1. Added Haversine spherical distance formula with 15-meter hysteresis buffer to `LocationService.ingestLocation()`.
  2. Automatically evaluates active child geofences and creates an `IncidentEntity` with WebSocket push on breach.
* **Status**: **RESOLVED & VERIFIED**.

---

### DEF-005 [SEVERITY: LOW] — Pairing Endpoint Path Aliases
* **Affected Module**: `backend/src/main/kotlin/com/safeband/backend/controller/Controllers.kt`
* **Reproduction Steps**: Client invokes `/api/v1/pairing/initiate` instead of `/api/v1/pairing/sessions`.
* **Expected Behavior**: Both legacy initiate and sessions endpoints return HTTP 200 OK.
* **Actual Behavior**: Returned HTTP 404 Not Found.
* **Root Cause**: Missing Spring request mapping alias on `PairingController`.
* **Files Modified**:
  * `backend/src/main/kotlin/com/safeband/backend/controller/Controllers.kt`
* **Fix Implemented**: Added `@PostMapping("/sessions", "/initiate")` and `@PostMapping("/{sessionId}/complete", "/{sessionId}/confirm")`.
* **Status**: **RESOLVED & VERIFIED**.

---

### DEF-006 [SEVERITY: CRITICAL] — ESP32-S3 GPIO 3/4 Dynamic Pulse Capacitive Settling Time
* **Affected Module**: `hardware/esp32-child-node/src/tamper_monitor.cpp`
* **Reproduction Steps**: Run physical continuity loop test on real silicone ribbon strap cable.
* **Expected Behavior**: Physical strap intact reads `STRAP_CLOSED`. Physical strap cut reads `STRAP_OPEN`.
* **Actual Behavior**: High parasitic capacitance caused spurious `STRAP_OPEN` detections with 20µs pulse.
* **Root Cause**: 20µs pulse was faster than RC rise time on internal 45kΩ pull-down.
* **Files Modified**:
  * `hardware/esp32-child-node/src/tamper_monitor.cpp`
* **Fix Implemented**: Increased pulse settling delay to 200µs (`delayMicroseconds(200)`).
* **Status**: **RESOLVED & COMPILED**. (Physical bench verification documented).

---

### DEF-007 [SEVERITY: MEDIUM] — Compose Action Button Letter-by-Letter Wrapping
* **Affected Module**: `app/src/main/java/com/example/ui/components/SafeBandComponents.kt`
* **Reproduction Steps**: Display long action text or run application under 150% font scale.
* **Expected Behavior**: Text remains on single line and truncates cleanly with ellipsis.
* **Actual Behavior**: Mid-word letter wrapping occurred in Jetpack Compose buttons.
* **Root Cause**: Missing `maxLines = 1` and `TextOverflow.Ellipsis`.
* **Files Modified**:
  * `app/src/main/java/com/example/ui/components/SafeBandComponents.kt`
* **Fix Implemented**: Added `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to `SafeBandPrimaryButton` and `SafeBandSecondaryButton`.
* **Status**: **RESOLVED & VERIFIED**.
