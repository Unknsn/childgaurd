# SafeBand Guardian v4.0.0 — Fault Recovery & Fault Injection Test Results

## 1. Scope & Execution Strategy

Fault injection tests deliberately induced hardware, process, network, and data corruption failures to verify system degradation states and automatic recovery.

---

## 2. Fault Injection Scenarios & Results

| Scenario ID | Fault Injected | Observed Degradation State | Recovery Behavior | Status |
| :--- | :--- | :--- | :--- | :--- |
| **FAULT-01** | Force-stop Android application while tracking | Foreground service stops; persistent notification cleared | Re-launching app restarts foreground service automatically | **PASS** |
| **FAULT-02** | Abrupt network cutoff during location upload | HttpURLConnection throws timeout; upload fails | Breadcrumb queued in Room; uploaded on network restore | **PASS** |
| **FAULT-03** | Backend server killed via `SIGKILL` | Mobile app receives connection refused | Retries with exponential backoff (10s, 20s, 40s); syncs upon restart | **PASS** |
| **FAULT-04** | Cloudflare Tunnel daemon killed | Public HTTPS URL returns 502/504 Bad Gateway | Reconnecting script re-establishes tunnel; URL updated | **PASS** |
| **FAULT-05** | Bluetooth adapter disabled in Android settings | BLE scanner stops; UI displays `HardwarePrerequisiteBanner` | Tapping banner prompts system Bluetooth enable dialog | **PASS** |
| **FAULT-06** | Location services (GPS) disabled | GPS polling stops; UI displays `HardwarePrerequisiteBanner` | Tapping banner opens system Location Settings | **PASS** |
| **FAULT-07** | ESP32-S3 wearable node reboot during active SOS | Node reboots; NVS flags preserve emergency state | BLE advertising resumes in emergency mode upon boot | **PASS** |
| **FAULT-08** | Intermittent strap continuity glitch (< 200ms) | Filtered by consecutive cycle threshold | Zero false tamper alerts generated | **PASS** |
| **FAULT-09** | Reconnection of severed strap | Node detects loop intact; keeps alert flag active | Does NOT silently erase incident until parent acknowledges | **PASS** |
| **FAULT-10** | Database file lock error on H2 | Multiple processes attempt opening file | `AUTO_SERVER=TRUE` enables concurrent process attachment | **PASS** |

---

## 3. Summary of Fault Resilience

SafeBand Guardian adheres to fail-safe principles: physical emergencies (SOS and strap cuts) lock into persistent states that cannot be silently cleared by device reboots, network dropouts, or temporary reconnection.
