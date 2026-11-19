# SafeBand Guardian v4.0.0 — Location Tracking Test Results

## 1. Scope & Verification Strategy

This report verifies the continuous 1-minute child location telemetry lifecycle, from GPS fix acquisition in `ChildLocationForegroundService` to backend ingestion and parent dashboard visualization.

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **LOC-01** | Foreground service startup on child mode launch | Starts `ChildLocationForegroundService` with notification | Android service test | **PASS** |
| **LOC-02** | High-accuracy GPS polling interval configuration | FusedLocationProvider configured for 60s target interval | Service code audit | **PASS** |
| **LOC-03** | GPS breadcrumb upload to `POST /api/v1/locations` | Stores coordinates in `locations` table, returns 200 OK | Backend integration test | **PASS** |
| **LOC-04** | Remote child location retrieval on parent phone | `GET /api/v1/locations/children/{childId}/latest` returns latest fix | Backend integration test | **PASS** |
| **LOC-05** | Parent dashboard remote child auto-refresh | Parent map and status cards update to remote child GPS | ViewModel unit test | **PASS** |
| **LOC-06** | Screen locked / application backgrounded | Service persists via ongoing notification and foreground type | Android lifecycle test | **PASS** |
| **LOC-07** | Network unavailable during location acquisition | Breadcrumbs buffered locally; uploaded on reconnection | Store and forward test | **PASS** |
| **LOC-08** | Wi-Fi to cellular data handover during location sync | HttpURLConnection completes or retries cleanly | Network simulation test | **PASS** |
| **LOC-09** | Malformed / invalid coordinates (NaN or > 90° lat) | Backend validation rejects breadcrumb with HTTP 400 | Backend unit test | **PASS** |
| **LOC-10** | Map coordinate centering without jumping to (0,0) | Verified location falls back to safe zone or last known fix | Compose UI test | **PASS** |

---

## 3. Findings & Operational Realities

* Under normal operating conditions on standard Android devices with battery optimization set to "Unrestricted", location fixes are acquired and uploaded at approximately 60-second intervals.
* On aggressive OEM Android skins (MIUI, EMUI), background execution may be throttled to 3–5 minutes during deep Doze mode; the persistent ongoing notification and battery optimization guide mitigate this behavior.
