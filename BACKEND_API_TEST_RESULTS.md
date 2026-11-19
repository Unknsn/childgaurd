# SafeBand Guardian v4.0.0 — Backend API Test Results

## 1. Scope & Verification Strategy

This report catalogs and verifies all REST endpoints exposed by the Spring Boot 3.4.2 backend service. Testing includes parameter validation, security filter enforcement, error handling, and response payloads.

---

## 2. Complete Endpoint Inventory & Verification Status

| Method | Path | Auth Required | Description / Test Scenario | Response | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/health` | No | Liveness and database connectivity check | `200 OK` | **PASS** |
| `POST` | `/api/v1/auth/google` | No | Google OAuth ID token exchange | `200 OK` | **PASS** |
| `POST` | `/api/v1/auth/dev-login` | No | Development mock parent/child login | `200 OK` | **PASS** |
| `POST` | `/api/v1/auth/refresh` | No | JWT refresh token exchange | `200 OK` | **PASS** |
| `POST` | `/api/v1/children` | Bearer JWT | Create new child profile | `200 OK` | **PASS** |
| `GET` | `/api/v1/children` | Bearer JWT | List parent's registered children | `200 OK` | **PASS** |
| `GET` | `/api/v1/children/{id}` | Bearer JWT | Get child details (with parent ownership check) | `200 OK` | **PASS** |
| `PUT` | `/api/v1/children/{id}` | Bearer JWT | Update child medical/emergency details | `200 OK` | **PASS** |
| `DELETE`| `/api/v1/children/{id}` | Bearer JWT | Delete child profile (cascades related data) | `204 No Content` | **PASS** |
| `POST` | `/api/v1/pairing/sessions` | Bearer JWT | Initiate Bluetooth challenge-response pairing | `200 OK` | **PASS** |
| `POST` | `/api/v1/pairing/initiate` | Bearer JWT | Initiate pairing alias | `200 OK` | **PASS** |
| `POST` | `/api/v1/pairing/{id}/complete` | Bearer JWT | Submit proof and finalize association | `200 OK` | **PASS** |
| `POST` | `/api/v1/pairing/{id}/confirm` | Bearer JWT | Finalize association alias | `200 OK` | **PASS** |
| `POST` | `/api/v1/locations` | No | Ingest 1-minute child GPS breadcrumb | `200 OK` | **PASS** |
| `POST` | `/api/v1/locations/batch` | No | Ingest offline buffered GPS coordinates | `200 OK` | **PASS** |
| `GET` | `/api/v1/locations/children/{id}/latest` | Bearer JWT | Retrieve most recent child location fix | `200 OK` | **PASS** |
| `GET` | `/api/v1/locations/children/{id}/history` | Bearer JWT | Retrieve paginated breadcrumb history | `200 OK` | **PASS** |
| `POST` | `/api/v1/geofences` | Bearer JWT | Create new circular or polygon safe zone | `200 OK` | **PASS** |
| `GET` | `/api/v1/geofences` | Bearer JWT | List active safe zones for child | `200 OK` | **PASS** |
| `POST` | `/api/v1/geofences/incidents/exit` | No | Report confirmed geofence exit breach | `200 OK` | **PASS** |
| `POST` | `/api/v1/incidents` | No | Report SOS or Tamper emergency incident | `200 OK` | **PASS** |
| `GET` | `/api/v1/incidents/{id}` | Bearer JWT | Retrieve incident dossier (isolated to parent) | `200 OK` | **PASS** |
| `POST` | `/api/v1/incidents/{id}/acknowledge` | Bearer JWT | Parent acknowledges incident | `200 OK` | **PASS** |
| `POST` | `/api/v1/incidents/{id}/resolve` | Bearer JWT | Parent resolves active emergency | `200 OK` | **PASS** |
| `POST` | `/api/v1/relay/observations` | No | Crowdsourced bystander observation submission | `200 OK` | **PASS** |
| `POST` | `/api/relay/alert` | No | Legacy community relay forwarding alias | `200 OK` | **PASS** |
| `POST` | `/api/v1/notifications/test` | Bearer JWT | Trigger test notification to verify pipeline | `200 OK` | **PASS** |

---

## 3. Negative & Boundary Testing

* **Missing Required Fields**: Returns HTTP 400 Bad Request with descriptive field error.
* **Malformed JSON**: Returns HTTP 400 Bad Request.
* **Expired / Invalid JWT**: Returns HTTP 401 Unauthorized.
* **Cross-Parent Ownership Violation**: Returns HTTP 403 Forbidden; parent A cannot access or modify parent B's child profiles or incident records.
