# SafeBand Guardian v4.0.0 — REST & WebSocket API Specification

## 1. Base URL & Protocol

* **Local Base URL**: `http://localhost:8080/api/v1`
* **Tunnel Base URL**: `https://<tunnel-subdomain>.trycloudflare.com/api/v1`
* **WebSocket URL**: `ws://localhost:8080/ws/parent` or `wss://<tunnel-subdomain>.trycloudflare.com/ws/parent`

---

## 2. Authentication Headers

Except for endpoints explicitly documented as `[Public]`, all requests require the HTTP Authorization header:
```http
Authorization: Bearer <jwt_access_token>
```

---

## 3. Endpoints

### 3.1. Health & Diagnostics
#### `GET /api/v1/health` [Public]
Returns backend server health and component status.
* **Response (200 OK)**:
```json
{
  "status": "UP",
  "version": "4.0.0",
  "database": "CONNECTED",
  "timestamp": "2026-11-19T04:40:00Z"
}
```

---

### 3.2. Authentication
#### `POST /api/v1/auth/google` [Public]
Exchanges a Google OAuth ID token for a SafeBand access and refresh token pair.
* **Request**:
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```
* **Response (200 OK)**:
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "7c8e9b...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "parent@example.com",
    "fullName": "Jane Doe"
  }
}
```

#### `POST /api/v1/auth/dev-login` [Public]
Development helper for testing parent and child sessions without Google Play Services.
* **Request**:
```json
{
  "email": "parent@safeband.local",
  "role": "PARENT"
}
```
* **Response (200 OK)**: Same as Google Auth response.

---

### 3.3. Child Management
#### `GET /api/v1/children`
List all children registered to the authenticated parent.

#### `POST /api/v1/children`
Register a new child profile.
* **Request**:
```json
{
  "firstName": "Alice",
  "lastName": "Doe",
  "dateOfBirth": "2018-05-14",
  "emergencyContactPhone": "+15551234567"
}
```

---

### 3.4. Wearable Devices & BLE Pairing
#### `POST /api/v1/devices/pairing/initiate`
Initiates a Bluetooth challenge-response pairing session.
* **Request**:
```json
{
  "childId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "deviceId": "SB-ESP32-A1B2"
}
```
* **Response (200 OK)**:
```json
{
  "sessionId": "9a1b2c3d-...",
  "challengeNonce": "f901ab88de..."
}
```

#### `POST /api/v1/devices/pairing/confirm`
Completes pairing by verifying proof from wearable.
* **Request**:
```json
{
  "sessionId": "9a1b2c3d-...",
  "proof": "e812ab99f4..."
}
```

---

### 3.5. Location Telemetry (1-Minute Intervals)
#### `POST /api/v1/locations` [Public or Authenticated]
Uploads current child device GPS coordinates from foreground service.
* **Request**:
```json
{
  "childId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "deviceId": "SB-ESP32-A1B2",
  "latitude": 37.774929,
  "longitude": -122.419416,
  "accuracyMeters": 4.5,
  "source": "CHILD_PHONE_GPS",
  "recordedAt": "2026-11-19T04:41:00Z"
}
```

#### `GET /api/v1/locations/child/{childId}/latest`
Retrieves the most recent known location for a child.

---

### 3.6. Geofences
#### `POST /api/v1/geofences`
Creates a circular or polygon safe zone.
* **Request**:
```json
{
  "childId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "School Zone",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "radiusMeters": 200.0,
  "dwellDelaySeconds": 60
}
```

#### `POST /api/v1/geofences/incidents/exit` [Public]
Reports a confirmed geofence exit breach event.

---

### 3.7. Emergency Incidents & Relay
#### `POST /api/v1/incidents` [Public]
Creates an immediate SOS or Tamper incident report.
* **Request**:
```json
{
  "childId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "deviceId": "SB-ESP32-A1B2",
  "incidentType": "TAMPER_DETECTED",
  "riskLevel": "CRITICAL",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "accuracyMeters": 5.0
}
```

#### `POST /api/v1/relay/observations` [Public]
Crowdsourced community observation of an emergency beacon.
* **Request**:
```json
{
  "incidentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "relayDeviceId": "RELAY-OBSERVER-99",
  "latitude": 37.7751,
  "longitude": -122.4192,
  "accuracyMeters": 8.0,
  "rssi": -72,
  "hopCount": 1,
  "observedAt": "2026-11-19T04:42:00Z"
}
```
