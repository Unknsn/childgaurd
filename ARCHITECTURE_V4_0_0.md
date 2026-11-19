# SafeBand Guardian v4.0.0 — System Architecture

## 1. High-Level Executive Summary

SafeBand Guardian v4.0.0 delivers an enterprise-grade, privacy-first child safety ecosystem integrating:
1. **ESP32-S3 Wearable Node**: Ultra-low-power BLE peripheral featuring hardware strap continuity tamper monitoring (GPIO 3/4), SOS panic trigger, authenticated GATT provisioning, and encrypted 0x5AFE advertisement broadcasts.
2. **Unified Android Application**: A dual-experience mobile application housing both Parent and Child modes within a single APK (`com.aistudio.safeband.xqmt`), featuring automated BLE provisioning, real-time geofence calculation, 1-minute child location tracking via foreground service, and anonymous community emergency relay scanning.
3. **Laptop-Hosted Spring Boot Backend**: High-performance Kotlin 2.2.10 backend based on Spring Boot 3.4.2, offering dual database persistence (Zero-dependency embedded H2 in PostgreSQL compatibility mode for instant local runs, and enterprise PostgreSQL for scale), Flyway automated migrations, Google OAuth2 token verification, and Spring WebSocket notifications.
4. **Zero-Configuration Secure Public Tunnel**: Automated Cloudflare Tunnel (`cloudflared`) orchestration exposing the laptop backend over a secure TLS endpoint accessible worldwide across any cellular or Wi-Fi network without dynamic DNS or router port forwarding.

```
+----------------------------------------------------------------------------------------------------+
|                                    SAFEBAND GUARDIAN v4.0.0 ECOSYSTEM                              |
+----------------------------------------------------------------------------------------------------+

   [ESP32-S3 Wearable]
        |  (BLE GATT Provisioning / AES-128-GCM)
        |  (0x5AFE Encrypted Advertisements / Tamper Beacon)
        v
   [Child Android Device] <---------------------------------------------+
   (ChildHomeScreen + Foreground Location Service)                      |
        |                                                               |
        | HTTPS REST (1-min GPS, SOS, Tamper)                           |
        v                                                               |
+-------------------------------------------------------------+         |
| PUBLIC INTERNET (Cloudflare Secure TLS Tunnel)              |         |
+-------------------------------------------------------------+         |
        |                                                               |
        v                                                               |
+-------------------------------------------------------------+         |
| LAPTOP-HOSTED SPRING BOOT BACKEND (:8080)                   |         |
| - Google Token Verifier / JWT Auth Filter                   |         |
| - Pairing & Hardware Association Service                    |         |
| - Geofence Evaluation Engine (Haversine + Dwell Hysteresis) |         |
| - Incident Management & Deduplication Service               |         |
| - Real-time WebSocket Broker (/ws/parent)                   |         |
| - Database: H2 Standalone (Local) / PostgreSQL (Prod)       |         |
+-------------------------------------------------------------+         |
        |                                                               |
        | WebSocket Alerts & HTTPS REST                                 |
        v                                                               |
   [Parent Android Device]                                              |
   (ParentHomeScreen, Map, Geofences, Device Pairing)                   |
                                                                        |
   [Nearby Community Smartphones]                                       |
   (Anonymous BLE Scanner -> HTTPS Incident Ingestion) -----------------+
```

---

## 2. Component Subsystems

### 2.1. Embedded Wearable Node (ESP32-S3)
* **Processor**: Espressif ESP32-S3 Dual-Core Xtensa LX7 @ 240MHz.
* **Firmware Framework**: Arduino-ESP32 Core via PlatformIO (`hardware/esp32-child-node`).
* **BLE Stack**: NimBLE-Arduino high-performance Bluetooth Low Energy stack.
* **Strap Continuity Tamper Detection**:
  * Pin pair: GPIO 3 (Output driver) and GPIO 4 (Input sensor).
  * Dynamic pulse mechanism: Pull-down input on GPIO 4, active pulse driven on GPIO 3 with a 200µs settling window to overcome ribbon line capacitance.
  * Rapid cut reaction: Sub-second transition to `ALERT_TAMPER` broadcast state with dedicated 0x5AFE advertisement payload.
* **Storage**: Non-Volatile Storage (NVS) partition preserving device ID, AES master key, parent public identity, and emergency state machine flags across reboots.

### 2.2. Android Mobile Application (`:app`)
* **Unified APK Architecture**:
  * **Parent Experience**: Google Sign-In authentication, device provisioning wizard via BLE scanning, live child tracking map (MapLibre / Google Maps compatible), interactive geofence visual polygon/circle builder, battery and BLE signal telemetry, and real-time emergency alert monitor with audible siren override.
  * **Child Experience**: Streamlined child dashboard (`ChildHomeScreen`), SOS panic button with physical button interception, and background `ChildLocationForegroundService` polling fine GPS coordinates every 60 seconds with offline-buffered HTTP synchronization.
  * **Community Relay Observer**: Headless background BLE observer detecting 0x5AFE emergency beacons from unknown children, extracting cryptographic hashes, appending observer GPS coordinates, and uploading to the backend without accessing or decrypting child identity.

### 2.3. Spring Boot Backend (`:backend`)
* **Technology**: Kotlin 2.2.10, Spring Boot 3.4.2, Spring Security 6.4, Spring Data JPA, Flyway 10.
* **Persistence Layer**:
  * Database schema with 10 tables: `users`, `child_profiles`, `wearable_devices`, `pairing_sessions`, `location_breadcrumbs`, `geofence_zones`, `incident_reports`, `relay_observations`, `notification_records`, and `audit_logs`.
  * Standalone mode: Embedded H2 database storing records at `./data/safeband.mv.db` in PostgreSQL compatibility mode.
  * Production mode: Switched via `SPRING_PROFILES_ACTIVE=postgres` to full PostgreSQL 16+ instance.
* **Real-time Alerting**: WebSocket endpoint `/ws/parent` with destination subscriptions for instant incident escalation.

### 2.4. Cloudflare Secure Tunnel
* **Daemon**: Native `cloudflared` agent packaged with zero local installation requirements.
* **Topology**: Outbound HTTPS/QUIC tunnel initiating from the developer laptop to Cloudflare's global edge network.
* **Routing**: Maps `localhost:8080` to a dynamically assigned or named public domain (`https://*.trycloudflare.com`), terminating TLS at edge and providing end-to-end encrypted transit from remote mobile cellular carriers back to the local backend.

---

## 3. Communication Protocols

| Link | Transport | Security / Encryption | Latency Target |
| :--- | :--- | :--- | :--- |
| **Wearable -> Phone (Telemetry)** | BLE 5.0 Advertisements (0x5AFE) | AES-128-GCM encrypted payload | < 100 ms |
| **Wearable <-> Phone (Pairing)** | BLE GATT Read/Write | Encrypted challenge-response | < 5 seconds |
| **Phone -> Backend (GPS & Alerts)** | HTTPS REST JSON | TLS 1.3 + Bearer JWT (Google Auth) | < 500 ms |
| **Backend -> Parent Phone (Alarms)** | WSS (WebSocket Secure) | TLS 1.3 + JWT handshake | < 200 ms |
| **Community Phone -> Backend (Relay)** | HTTPS REST JSON | Anonymous API token + Nonce HMAC | < 2 seconds |

---

## 4. Hardware-Enforced Safety Boundaries

1. **Tamper Loop Circuit**: Closed-circuit conductive strap monitored through dynamic GPIO pulsing. Any physical cut, open circuit, or voltage clamp triggers immediate local and wireless panic dispatch.
2. **Maintenance Mode Window**: Tamper triggers can only be disarmed through authenticated parent GATT write (`CMD_MAINTENANCE_ENTER`) accompanied by a time-bounded challenge token valid for 5 minutes.
3. **Emergency Power Conservation**: On battery < 15%, ESP32 suspends non-essential peripheral clocks and throttles normal telemetry to 5-minute intervals, while preserving immediate interrupt-driven wakeups for GPIO tamper and button SOS.
