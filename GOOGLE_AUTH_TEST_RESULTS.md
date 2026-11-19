# SafeBand Guardian v4.0.0 — Google Authentication Test Results

## 1. Scope & Verification Strategy

This report details the verification of Google OAuth2 authentication across the Android mobile client and the Spring Boot backend service.

---

## 2. Test Execution Matrix

| Test Case ID | Scenario Description | Expected Outcome | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **AUTH-01** | Development parent token exchange via `/api/v1/auth/dev-login` | Returns valid JWT access & refresh tokens with PARENT role | Automated integration test | **PASS** |
| **AUTH-02** | Development child token exchange via `/api/v1/auth/dev-login` | Returns valid JWT access & refresh tokens with CHILD role | Automated integration test | **PASS** |
| **AUTH-03** | Google ID token verification with valid cryptographic signature | Decodes payload, provisions UserEntity, returns JWT pair | Backend unit test | **PASS** |
| **AUTH-04** | Google ID token with expired timestamp | Rejected with HTTP 401 Unauthorized | Backend unit test | **PASS** |
| **AUTH-05** | Google ID token with mismatched audience (`aud != clientId`) | Rejected with HTTP 401 Unauthorized | Backend unit test | **PASS** |
| **AUTH-06** | Google ID token with forged RSA signature | Rejected with HTTP 401 Unauthorized | Backend unit test | **PASS** |
| **AUTH-07** | Tampered JWT access token in `Authorization: Bearer` | Intercepted by `JwtAuthenticationFilter`, returns HTTP 403/401 | Automated integration test | **PASS** |
| **AUTH-08** | Expired JWT access token (1-hour TTL) | Rejected by backend filter | Automated integration test | **PASS** |
| **AUTH-09** | Token refresh via `POST /api/v1/auth/refresh` | Generates new unexpired access token from valid refresh token | Backend integration test | **PASS** |
| **AUTH-10** | Live Google Sign-In button on Android device | Prompts Google Account picker via Credential Manager | Physical Android test | **BLOCKED BY EXTERNAL CONFIGURATION** |

---

## 3. External Dependencies & Configuration Status

* **Status**: **BLOCKED BY EXTERNAL CONFIGURATION** for live Play Services account picker on production Android builds.
* **Reason**: Live Google Sign-In requires registering the debug SHA-1 keystore fingerprint in Google Cloud Console under an OAuth 2.0 Web Client ID.
* **Verified Keystore Fingerprints**:
  * SHA-1: `9C:BD:EB:7C:C6:26:AC:0E:3E:89:67:25:9D:16:10:80:FA:1A:7C:D7`
  * SHA-256: `51:19:1E:41:E6:BB:7F:A3:F8:B8:FB:B4:A7:BA:A6:99:B0:3D:B4:63:03:9C:34:96:AC:9D:71:3D:B6:84:65:84`
* **Development Fallback**: Developers and QA can authenticate instantly via `/api/v1/auth/dev-login` without external credentials or internet access.
