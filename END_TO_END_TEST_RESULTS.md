# SafeBand Guardian v4.0.0 — End-to-End Integration Scenarios

## 1. Executive Summary

This report documents the verification of 9 complete end-to-end user and system journeys across all subsystems: Parent App, Child App, ESP32-S3 Wearable Node, Laptop Backend, and Public Cloudflare Tunnel.

---

## 2. Scenario Results

### Scenario A: New Family Registration & Onboarding
1. Parent opens app and selects Parent Mode on `FirstLaunchScreen`.
2. Authenticates via `/api/v1/auth/dev-login` or Google OAuth2.
3. Registers child profile "Alice Doe" via `POST /api/v1/children`.
4. Enters `ChildProvisioningScreen` to initiate BLE challenge-response pairing with ESP32 wearable `SB-CHLD-TEST1`.
5. Backend verifies HMAC challenge proof and associates hardware MAC to child.
* **Verification**: `BackendApiIntegrationTest.kt` step 1–3.
* **Status**: **PASS**.

### Scenario B: Routine Child Movement & 1-Minute Tracking
1. Child wears ESP32 node; phone runs `ChildLocationForegroundService`.
2. FusedLocationProvider acquires high-accuracy GPS fix every 60 seconds.
3. Breadcrumbs uploaded to `POST /api/v1/locations`.
4. Remote parent app auto-polls `GET /api/v1/locations/children/{id}/latest` and updates map marker.
5. Child remains inside home safe zone; zero emergency relays triggered.
* **Verification**: `BackendApiIntegrationTest.kt` step 4.
* **Status**: **PASS**.

### Scenario C: Geofence Breach Detection
1. Child moves to coordinates outside the configured 150m safe zone radius.
2. Location breadcrumb uploaded to backend.
3. Backend `LocationService` computes Haversine distance (> radius + 15m hysteresis).
4. `IncidentEntity` created with `incidentType = "GEOFENCE_EXIT"`.
5. WebSocket event `GEOFENCE_EXIT_INCIDENT` pushed to parent device.
* **Verification**: `BackendApiIntegrationTest.kt` step 5–6.
* **Status**: **PASS**.

### Scenario D: Physical Strap Tampering (GPIO 3/4)
1. Physical continuity loop on ESP32-S3 cut or open-circuited.
2. Firmware detects severed loop after 200µs pulse test; switches state to `ALERT_TAMPER`.
3. BLE advertisement interval accelerates to 200ms with flag `0x03`.
4. Nearby phone decodes `alertType = "TAMPER"`, logs `TAMPER_CONFIRMED` in Room, and uploads incident to backend.
5. Parent alarm sheet presents audible siren override.
* **Verification**: `BleSafetyManagerTest.kt` & firmware logic audit.
* **Status**: **PASS** (Simulated hardware).

### Scenario E: SOS Panic with Community Relay Mesh
1. Child triggers SOS button panic; out of parent's direct BLE range.
2. Participating bystander phone detects 0x5AFE panic beacon.
3. Bystander app extracts beacon hash, attaches its own GPS coordinates, and submits to `POST /api/v1/relay/observations`.
4. Observer phone sees zero child PII.
5. Backend deduplicates observation and pushes updated location to parent via WebSocket.
* **Verification**: `BackendApiIntegrationTest.kt` step 7–9.
* **Status**: **PASS**.

### Scenario F: Communication Across Different Networks
1. Backend runs on laptop connected to home Wi-Fi; Cloudflare Tunnel is active.
2. Parent phone is on cellular 5G network.
3. Child phone is on a separate Wi-Fi network.
4. Both devices communicate securely via TLS 1.3 endpoints on `https://*.trycloudflare.com`.
* **Verification**: Public tunnel routing & health endpoint verification.
* **Status**: **PASS**.

### Scenario G: Complete Network Outage & Recovery
1. Mobile network disconnected while child is in transit.
2. GPS coordinates buffered in local Room database.
3. Emergency state machine remains active.
4. Cellular connection restored; pending queue flushes cleanly to backend.
* **Verification**: `StoreAndForwardTest.kt`.
* **Status**: **PASS**.

### Scenario H: Cross-Account Security & Authorization Boundary
1. Parent A registers Child A; Parent B registers Child B.
2. Parent B attempts `GET /api/v1/children/{childA_id}` or `GET /api/v1/incidents/{incidentA_id}`.
3. Spring Security intercepts request and returns HTTP 403 Forbidden.
* **Verification**: Security unit & MockMvc tests.
* **Status**: **PASS**.

### Scenario I: Full System Restart Recovery
1. Backend shut down via `stop-backend.bat`.
2. Laptop restarted; backend launched via `start-backend.bat`.
3. Standalone H2 file database `./backend/data/safeband.mv.db` mounts cleanly.
4. Mobile apps reconnect and resume telemetry synchronization without data loss.
* **Verification**: Database persistence test.
* **Status**: **PASS**.
