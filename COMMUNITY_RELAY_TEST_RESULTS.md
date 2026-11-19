# SafeBand Guardian v4.0.0 — Community Emergency Relay Test Results

## 1. Scope & Verification Strategy

This report evaluates the crowdsourced community BLE mesh relay subsystem. It verifies privacy guarantees (zero child PII), emergency-only ingestion, observer anonymity, backend deduplication, and parent notification.

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **RELAY-01** | Normal heartbeat beacon (`0x00`) detected by nearby phone | Ignored by community relay; zero location collected | Automated BLE unit test | **PASS** |
| **RELAY-02** | Emergency SOS beacon (`0x02`) detected by nearby phone | Ingests beacon hash, acquires observer GPS fix, queues record | `EmergencyRelayManagerTest.kt` | **PASS** |
| **RELAY-03** | Physical tamper cut beacon (`0x03`) detected | Ingests beacon hash, acquires observer GPS fix, queues record | `EmergencyRelayManagerTest.kt` | **PASS** |
| **RELAY-04** | Privacy audit on relaying smartphone | Child name, parent phone, and medical notes are NEVER logged | Privacy audit test | **PASS** |
| **RELAY-05** | Honest GPS policy (no fake coordinates) | If observer has no GPS fix, coordinates remain `null` | Service unit test | **PASS** |
| **RELAY-06** | Late-arriving GPS fix enrichment | Pending relay record enriched when GPS becomes available | Service unit test | **PASS** |
| **RELAY-07** | Upload relay observation to backend `POST /api/v1/relay/observations` | Ingests observation, links to active incident, updates location | Backend integration test | **PASS** |
| **RELAY-08** | Legacy endpoint compatibility (`POST /api/relay/alert`) | Ingests legacy JSON format without error (HTTP 200 OK) | Backend API test | **PASS** |
| **RELAY-09** | Deduplication of duplicate sightings within 60s window | Returns existing observation; suppresses flood alerts | Backend unit test | **PASS** |
| **RELAY-10** | Real-time WebSocket `RELAY_OBSERVATION_RECEIVED` alert | Pushed to authorized parent with updated coordinates | WebSocket integration test | **PASS** |
| **RELAY-11** | Relay observation queue drain on network restore | Offline Room queue automatically drains when internet returns | Store and forward test | **PASS** |
| **RELAY-12** | Multi-phone physical relay test across 2 separate Android devices | Real BLE transmission and remote cellular relay ingestion | Physical Android device test | **SIMULATED PASS** (Physical bench pending) |

---

## 3. Android Background Execution Restrictions & Mitigation

* **Operating System Boundaries**: On Android 12+, background BLE scanning without an active foreground service is throttled or suspended during Doze mode.
* **Architectural Decision in v4.0.0**: SafeBand Guardian participants running in Community Relay mode leverage `EmergencyRelayManager` backed by `BleSafetyMonitorService`, ensuring continuous passive emergency beacon detection while adhering to Android power policies.
