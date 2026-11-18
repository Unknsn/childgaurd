# SafeBand Guardian: Parent-Child Connectivity, Emergency Relay and Hardware Tamper Detection Audit

**Project:** SafeBand Guardian / ChildGuard (`com.aistudio.safeband.xqmt`)  
**Audit Scope:** Parent-Child Provisioning, Community-Assisted Emergency Alert Relay, GPIO3/GPIO4 Hardware Tamper Detection  
**Hardware Node:** Espressif ESP32-S3 Wearable Node (ESP-IDF / Arduino Framework)  
**Mobile Client:** Single-APK Android Application (Parent & Child Roles)  
**Date:** November 2026  

---

## 1. Executive Summary

This repository audit evaluates the three required features across both the Android application codebase and the ESP32-S3 firmware codebase:
1. **Feature 1: Parent-Child Provisioning & Connectivity**
2. **Feature 2: Community-Assisted Emergency Alert Propagation**
3. **Feature 3: GPIO3 and GPIO4 Hardware Tamper Detection with Parent-Authorized Maintenance**

---

## 2. Feature-by-Feature Technical Audit

| Feature | Existing Implementation | Missing Functionality | Required Changes |
| :--- | :--- | :--- | :--- |
| **Parent-child provisioning** | In-app 6-digit challenge system in `DeviceProvisioningManager.kt` generates 15-min TTL challenge codes between Android devices. `paired_children` entity stores child profile. | No direct over-the-air BLE GATT provisioning protocol to transmit parent endpoint, routing tokens, and child credentials to the physical ESP32-S3 wearable node. | Implement BLE GATT provisioning service/characteristic (`00005afe-0002-...`) on ESP32-S3 and `BleProvisioningManager.kt` in Android. Transfer child ID, parent routing ID, parent node IP/port, and tokens securely. |
| **Parent node IP configuration** | Hardcoded or absent in child entity; no UI or persistent configuration for parent node communication endpoints (IP, port, endpoint ID). | No parent-side configuration dialog to set/update parent node IP and port; no network-adaptive direct routing. | Add parent node IP, port, and endpoint ID fields to `paired_children` in Room (Schema v6 migration) and `SafeBandPreferences`. Provide endpoint configuration UI on Parent Dashboard. |
| **Child configuration storage** | ESP32-S3 firmware hardcodes static identity (`SafeBandNodeIdentity::init("SB-CHLD")`); child profile struct exists in memory only. | Non-volatile storage (NVS) is not utilized in firmware; configuration is lost on reboot or power cycle. | Integrate ESP32 `Preferences` (NVS) to persistently store provisioned child ID, parent ID, parent node IP/port, config version, and tamper maintenance state. |
| **BLE emergency broadcast** | `safeband_ble.cpp` broadcasts 24-byte payload under company ID `0x5AFE` with risk level byte (Normal vs Critical SOS). | Broadcast does not distinguish between SOS triggers and Hardware Tamper triggers; lacks sequence numbers and tamper event codes. | Expand manufacturer payload risk/event byte to distinguish `0x03` (SOS) and `0x04` (TAMPER). Ensure privacy boundary: no plain child names, addresses, or parent phone numbers in public broadcast. |
| **Nearby phone detection** | `BleSafetyManager.kt` and `BleSafetyMonitorService.kt` detect `0x5AFE` packets and extract risk level. | Nearby phones do not show a dedicated privacy-preserving relay alert interface or capture observer device location on alert detection. | Implement `RelayEmergencyScreen` / banner showing emergency detected, event category, detection time, relay status, and privacy statement without leaking child PII. |
| **Automatic relay** | `SafetyRepository.kt` defines `SyncStatus.RELAYED` and event sync states, but no automated forwarding daemon runs on nearby phones. | Detection does not trigger an automatic background dispatch to the parent node IP or cloud backend without user intervention. | Implement `EmergencyRelayManager.kt`: automatically packages incident ID, protected device reference, relay location, and attempts immediate direct or backend delivery. |
| **Relay location forwarding** | Location is logged locally from child phone GPS when child phone is source; relay phone does not attach its own Android location provider fix. | Relay phone GPS coordinates, accuracy, and timestamp are not captured upon emergency packet discovery and forwarded. | Integrate Android `FusedLocationProviderClient` in `EmergencyRelayManager.kt` to capture one-shot relay fix with accuracy and timestamp, forwarding it as a relay observation. |
| **Parent notification** | `BleSafetyMonitorService.kt` posts local notification when emergency beacon is detected. | Parent cannot distinguish between child's direct location and a nearby bystander phone's detection location. | Update Parent Dashboard incident view to display clear banner: **"Emergency detected near this location by a participating device."** with relay accuracy radius. |
| **GPIO3/GPIO4 monitoring** | Firmware `config.h` defines only `PIN_BOOT_BUTTON 0` and `PIN_RGB_LED 48`. GPIO3 and GPIO4 are not defined or initialized. | Zero hardware tamper detection logic, pin toggling, or electrical continuity verification in ESP32 firmware. | Implement `TamperMonitor` on ESP32-S3 using GPIO3 as output toggle (heartbeat TX) and GPIO4 as input pull-down (RX). Debounce readings to reject electrical noise. |
| **Tamper state machine** | `child_profile.h` defines basic `NodeState` (Initializing, Normal, SOS, Error). No tamper states exist. | Missing required states: `INITIALIZING`, `PROTECTED`, `SUSPECTED_TAMPER`, `TAMPER_CONFIRMED`, `AUTHORIZED_DISCONNECTION`, `RESTORING`, `SENSOR_FAULT`. | Implement full tamper state machine in firmware and mirror states in Android model (`TamperState` enum). |
| **Parent maintenance authorization** | No maintenance or authorized disconnection mechanism exists in Android or firmware. | Parent cannot temporarily authorize band removal or strap change without triggering false alarms. | Implement `SAFEBAND_CHAR_MAINTENANCE_UUID` on ESP32-S3 and `BleTamperMaintenanceManager.kt` in Android. Allow parent to grant 5m, 15m, 30m maintenance windows with auto-expiry. |
| **Tamper emergency escalation** | Only manual SOS button press transitions node to `NODE_SOS_ACTIVE`. | Tamper events do not automatically initiate emergency BLE broadcast or integrate with the Android emergency pipeline. | When tamper transitions to `TAMPER_CONFIRMED`, automatically escalate node to emergency state, broadcast tamper beacon, and route through store-and-forward relay. |

---

## 3. Database Schema Evolution (Schema v6 Migration)

To support the three features cleanly without breaking existing data:
1. **Extend `paired_children` table**:
   - `parentNodeIp`: Parent node network address (e.g., `192.168.1.100`)
   - `parentNodePort`: Parent node port (e.g., `8080`)
   - `parentNodeEndpointId`: Unique parent device identifier
   - `tamperStatus`: Current tamper status (`PROTECTED`, `SUSPECTED`, `CONFIRMED`, `AUTHORIZED_MAINTENANCE`, `SENSOR_FAULT`)
   - `tamperAuthorizedUntil`: Unix timestamp when authorized maintenance window expires
   - `isProvisioned`: Boolean flag indicating physical ESP32 node has confirmed provisioning
   - `provisionedAt`: Timestamp of last successful configuration handshake
2. **Create `relay_forwarding_records` table**:
   - Tracks incoming emergency packets detected by this phone (acting as a relay node)
   - Columns: `id`, `incidentId`, `deviceId`, `eventType`, `detectionTimestamp`, `relayLatitude`, `relayLongitude`, `relayAccuracy`, `forwardStatus`, `targetEndpoint`, `attempts`, `lastAttemptTimestamp`, `deliveryTimestamp`, `acknowledgmentToken`.
