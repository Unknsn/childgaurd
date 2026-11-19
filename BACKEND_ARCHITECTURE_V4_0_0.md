# SafeBand Guardian v4.0.0 — Backend Architecture Specification

## 1. Architectural Overview

The SafeBand Guardian Backend is a reactive, resilient Spring Boot 3.4.2 application written in Kotlin 2.2.10. It is engineered to operate seamlessly in two distinct execution environments:
1. **Local Developer Laptop**: Running in zero-dependency `standalone` mode backed by persistent embedded H2 in PostgreSQL compatibility mode, exposed worldwide via an automated Cloudflare Tunnel.
2. **Production Cloud Environment**: Running in `postgres` mode backed by an enterprise PostgreSQL cluster and distributed Redis cache.

```
                                  +------------------------+
                                  |    Spring Security     |
                                  |  JwtAuthenticationFilter|
                                  +-----------+------------+
                                              |
      +-------------------+-------------------+--------------------+--------------------+
      |                   |                   |                    |                    |
+-----v-------+     +-----v-------+     +-----v--------+     +-----v--------+     +-----v--------+
|    Auth     |     | Child & Dev |     |   Location   |     |    Incident  |     |   WebSocket  |
|  Controller |     | Controllers |     | & Geofencing |     |   & Relay    |     |    Handler   |
+-----+-------+     +-----+-------+     +-----+--------+     +-----+--------+     +-----+--------+
      |                   |                   |                    |                    |
+-----v-------+     +-----v-------+     +-----v--------+     +-----v--------+           |
| AuthService |     |Child/Device |     | LocationSvc  |     | IncidentSvc  |           |
| GoogleVerify|     | PairingSvc  |     | GeofenceSvc  |     | RelayService |           |
+-----+-------+     +-----+-------+     +-----+--------+     +-----+--------+           |
      |                   |                   |                    |                    |
      +-------------------+-------------------+--------------------+                    |
                                              |                                         |
                                    +---------v----------+                              |
                                    | Spring Data JPA /  |                              |
                                    | Hibernate Layer    |                              |
                                    +---------+----------+                              |
                                              |                                         |
                                    +---------v----------+                              |
                                    | Flyway Schema V1   |                              |
                                    +---------+----------+                              |
                                              |                                         |
                                 +------------+------------+                            |
                                 |                         |                            |
                         +-------v-------+         +-------v-------+                    |
                         | H2 Standalone |         | PostgreSQL 16 |                    |
                         | (Local/Tunnel)|         | (Production)  |                    |
                         +---------------+         +---------------+                    |
                                                                                        |
                                 +------------------------------------------------------+
                                 | Broadcasts (INCIDENT_CREATED, LOCATION_UPDATE)
                                 v
                        [WebSocket Clients /ws/parent]
```

---

## 2. Security Filter Chain & Authentication

### 2.1. Authentication Architecture
The backend enforces a stateless JWT-based security model:
* **Public Endpoints**:
  * `GET /api/v1/health` (Liveness and readiness checks)
  * `POST /api/v1/auth/google` (Google ID token exchange)
  * `POST /api/v1/auth/dev-login` (Development parent/child credential token exchange)
  * `POST /api/v1/auth/refresh` (JWT refresh token rotation)
  * `POST /api/v1/relay/observe` (Anonymous community mesh observer incident forwarding)
* **Protected Endpoints**:
  * All `/api/v1/children/**`, `/api/v1/devices/**`, `/api/v1/locations/**`, `/api/v1/geofences/**`, `/api/v1/incidents/**`, and `/api/v1/notifications/**` endpoints require a valid `Authorization: Bearer <jwt_access_token>`.

### 2.2. Google OAuth2 ID Token Verification
In production, `GoogleTokenVerifierService` validates Google ID tokens against Google's public keys via `GoogleIdTokenVerifier`. It confirms:
1. Valid RSA cryptographic signature from `accounts.google.com`.
2. Audience matches the configured `GOOGLE_CLIENT_ID`.
3. Token expiration timestamp is in the future.
4. Auto-provisions or links the internal `UserEntity` with email and verified name.

---

## 3. Data Model & Flyway Migration Schema (V1)

The backend schema is initialized via `V1__init_schema.sql` across 10 tables:

### 3.1. `users` Table
Stores authenticated parents and registered guardians.
* `id` (VARCHAR(36), PK): UUID
* `email` (VARCHAR(255), UNIQUE, NOT NULL)
* `full_name` (VARCHAR(255))
* `google_id` (VARCHAR(255), UNIQUE)
* `phone_number` (VARCHAR(50))
* `fcm_token` (VARCHAR(512))
* `created_at`, `updated_at` (TIMESTAMP)

### 3.2. `child_profiles` Table
Stores registered children linked to a parent user.
* `id` (VARCHAR(36), PK): UUID
* `parent_id` (VARCHAR(36), FK -> users.id)
* `first_name`, `last_name` (VARCHAR(100), NOT NULL)
* `date_of_birth` (DATE)
* `avatar_url` (VARCHAR(512))
* `emergency_contact_phone` (VARCHAR(50))
* `status` (VARCHAR(50)): `ACTIVE`, `SOS_TRIGGERED`, `TAMPER_ALERT`, `SAFE`

### 3.3. `wearable_devices` Table
Tracks registered ESP32-S3 physical wearable nodes.
* `device_id` (VARCHAR(64), PK): MAC address or SHA-256 hardware identifier
* `child_id` (VARCHAR(36), FK -> child_profiles.id, UNIQUE, NULLABLE)
* `device_type` (VARCHAR(50)): `ESP32_S3`
* `firmware_version` (VARCHAR(50))
* `aes_master_key` (VARCHAR(64)): Hex-encoded AES-128 key for beacon decryption
* `battery_level` (INT)
* `is_tamper_tripped` (BOOLEAN)
* `maintenance_mode_until` (TIMESTAMP)
* `last_seen_at` (TIMESTAMP)

### 3.4. `pairing_sessions` Table
Manages challenge-response handshakes during Bluetooth provisioning.
* `session_id` (VARCHAR(36), PK)
* `child_id` (VARCHAR(36), FK)
* `device_id` (VARCHAR(64))
* `challenge_nonce` (VARCHAR(64), NOT NULL)
* `status` (VARCHAR(50)): `INITIATED`, `CHALLENGED`, `CONFIRMED`, `EXPIRED`
* `expires_at` (TIMESTAMP)

### 3.5. `location_breadcrumbs` Table
High-frequency 60-second periodic GPS telemetry.
* `id` (VARCHAR(36), PK)
* `child_id` (VARCHAR(36), FK)
* `device_id` (VARCHAR(64), NULLABLE)
* `latitude` (DOUBLE PRECISION, NOT NULL)
* `longitude` (DOUBLE PRECISION, NOT NULL)
* `accuracy_meters` (FLOAT)
* `altitude` (FLOAT)
* `speed` (FLOAT)
* `bearing` (FLOAT)
* `source` (VARCHAR(50)): `CHILD_PHONE_GPS`, `RELAY_ESTIMATE`, `CELL_TOWER`
* `recorded_at` (TIMESTAMP, NOT NULL)

### 3.6. `geofence_zones` Table
Polygon and circular safety perimeters.
* `id` (VARCHAR(36), PK)
* `child_id` (VARCHAR(36), FK)
* `name` (VARCHAR(100), NOT NULL)
* `center_latitude`, `center_longitude` (DOUBLE PRECISION, NOT NULL)
* `radius_meters` (FLOAT, NOT NULL)
* `polygon_geojson` (TEXT, NULLABLE)
* `dwell_delay_seconds` (INT, DEFAULT 60)
* `is_active` (BOOLEAN, DEFAULT TRUE)

### 3.7. `incident_reports` Table
Emergency events (SOS button panic, GPIO tamper cut, geofence breaches).
* `id` (VARCHAR(36), PK)
* `child_id` (VARCHAR(36), FK)
* `incident_type` (VARCHAR(50)): `SOS_MANUAL`, `TAMPER_DETECTED`, `GEOFENCE_BREACH`
* `status` (VARCHAR(50)): `TRIGGERED`, `ACKNOWLEDGED`, `RESOLVED`, `FALSE_ALARM`
* `trigger_source` (VARCHAR(50)): `WEARABLE_BLE`, `PHONE_UI`, `COMMUNITY_RELAY`
* `latitude`, `longitude` (DOUBLE PRECISION)
* `payload_json` (TEXT)
* `triggered_at`, `resolved_at` (TIMESTAMP)

### 3.8. `relay_observations` Table
Crowdsourced community BLE mesh observations.
* `id` (VARCHAR(36), PK)
* `incident_id` (VARCHAR(36), FK, NULLABLE)
* `observer_token_hash` (VARCHAR(64), NOT NULL)
* `anonymized_beacon_hash` (VARCHAR(64), NOT NULL)
* `observer_latitude`, `observer_longitude` (DOUBLE PRECISION, NOT NULL)
* `observer_accuracy_meters` (FLOAT)
* `rssi` (INT)
* `observed_at` (TIMESTAMP)

### 3.9. `notification_records` Table
Log of outbound push and WebSocket alarms sent to parents.
* `id` (VARCHAR(36), PK)
* `user_id` (VARCHAR(36), FK)
* `incident_id` (VARCHAR(36), FK, NULLABLE)
* `channel` (VARCHAR(50)): `FCM_PUSH`, `WEBSOCKET`, `SMS`
* `title`, `body` (VARCHAR(255))
* `status` (VARCHAR(50)): `SENT`, `DELIVERED`, `FAILED`

### 3.10. `audit_logs` Table
Immutable operational and administrative audit trail.
* `id` (VARCHAR(36), PK)
* `user_id` (VARCHAR(36), NULLABLE)
* `action` (VARCHAR(100), NOT NULL)
* `entity_type`, `entity_id` (VARCHAR(100))
* `ip_address` (VARCHAR(50))
* `details_json` (TEXT)
* `timestamp` (TIMESTAMP)

---

## 4. Real-Time WebSocket Architecture

1. **Endpoint**: `/ws/parent`
2. **Security**: Evaluates `Sec-WebSocket-Protocol` or query parameter `token` with `JwtTokenProvider`.
3. **Payload Structure**:
```json
{
  "event": "INCIDENT_CREATED",
  "data": {
    "incidentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "childId": "c8b417e2-4521-4682-a9b0-9118e4726a11",
    "incidentType": "TAMPER_DETECTED",
    "status": "TRIGGERED",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "triggeredAt": "2026-11-19T04:30:00Z"
  }
}
```
4. **Resilience**: Client automatically reconnects with exponential backoff on network dropouts.
