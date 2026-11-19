# SafeBand Guardian v4.0.0 — Notification & Alert Delivery Test Results

## 1. Scope & Verification Strategy

This report verifies the alert notification pipeline: local Android high-priority notifications, WebSocket real-time event dispatch, and Firebase Cloud Messaging (FCM).

---

## 2. Test Execution Matrix

| Test Case ID | Alert Scenario | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **NOTIF-01** | Manual SOS panic triggered by child | Local siren audio plays, WebSocket event broadcast to parent | ViewModel & audio test | **PASS** |
| **NOTIF-02** | Physical strap cut (GPIO 3/4 severed) | Critical tamper notification and siren alarm triggered | BLE alert test | **PASS** |
| **NOTIF-03** | Geofence boundary exit breach | `GEOFENCE_EXIT_INCIDENT` alert delivered via WebSocket | WebSocket integration test | **PASS** |
| **NOTIF-04** | Nearby bystander relay sighting | `RELAY_OBSERVATION_RECEIVED` pushed with bystander coordinates | WebSocket integration test | **PASS** |
| **NOTIF-05** | Notification channel creation on Android 8+ | Channels created: `safeband_child_location_channel`, `safeband_community_relay`, `safeband_alerts` | Android service test | **PASS** |
| **NOTIF-06** | Android 13+ `POST_NOTIFICATIONS` runtime permission | Checks permission before posting; displays rationale dialog if denied | Permission test | **PASS** |
| **NOTIF-07** | Audio alert override on silent/vibrate device profile | `AudioAttributes.USAGE_ALARM` with max stream volume | `AlertNotifierTest.kt` | **PASS** |
| **NOTIF-08** | Notification history persistence | Outbound notifications logged in backend `notification_records` | Backend integration test | **PASS** |
| **NOTIF-09** | Live Firebase Cloud Messaging (FCM) Push Delivery | Wakes sleeping Android device from deep Doze mode | Cloud push test | **BLOCKED BY EXTERNAL CONFIGURATION** |

---

## 3. FCM Push Notification Configuration Disclosure

* **Status**: **BLOCKED BY EXTERNAL CONFIGURATION** for live FCM cloud delivery.
* **Reason**: Requires `google-services.json` and a valid Firebase service account JSON key mounted in backend `FIREBASE_CONFIG_PATH`.
* **Local & Real-Time Delivery**: Local high-priority Android notifications and Spring WebSocket real-time push alerts are **100% functional and verified**.
