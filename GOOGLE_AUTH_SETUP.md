# SafeBand Guardian v4.0.0 — Google Authentication Setup Guide

This guide provides the exact, step-by-step instructions to configure production Google Sign-In via Android Credential Manager and Spring Boot Backend Token Verification.

---

## 1. Google Cloud Console Configuration

### Step 1: Create or Select Google Cloud Project
1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/).
2. Click the project dropdown at the top and select **New Project**.
3. Name the project `safeband-guardian` and click **Create**.

### Step 2: Configure OAuth Consent Screen
1. In the left navigation menu, go to **APIs & Services** ➜ **OAuth consent screen**.
2. Select **External** (or **Internal** if using Google Workspace) and click **Create**.
3. Complete the app details:
   - **App name:** `SafeBand Guardian`
   - **User support email:** Your email address
   - **Developer contact information:** Your email address
4. Under **Scopes**, click **Add or Remove Scopes** and select:
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `openid`
5. Click **Save and Continue**.
6. Under **Test users**, add your personal Google account email address so you can sign in during development.

---

## 2. Generate OAuth 2.0 Credentials

You must generate **TWO** credentials in Google Cloud Console:
1. **Web Application Client ID** (used by backend to verify tokens and by Android as `serverClientId`)
2. **Android Client ID** (used by Google Play Services on Android to display native account picker)

### Credential A: Web Application Client ID (Server Client)
1. Go to **APIs & Services** ➜ **Credentials**.
2. Click **+ CREATE CREDENTIALS** ➜ **OAuth client ID**.
3. Select Application type: **Web application**.
4. Name: `SafeBand Backend Server`.
5. Under **Authorized redirect URIs**, add:
   - `http://localhost:8080/login/oauth2/code/google`
   - `https://your-public-tunnel-url.trycloudflare.com/login/oauth2/code/google`
6. Click **Create**.
7. Copy the generated **Client ID** (format: `104192847120-xxxxxxxx.apps.googleusercontent.com`).
8. Place this Client ID into:
   - Backend `.env`: `GOOGLE_CLIENT_ID=104192847120-xxxxxxxx.apps.googleusercontent.com`
   - Android `app/src/main/res/values/strings.xml`: `<string name="default_web_client_id">104192847120-xxxxxxxx.apps.googleusercontent.com</string>`

### Credential B: Android Client ID
1. Click **+ CREATE CREDENTIALS** ➜ **OAuth client ID**.
2. Select Application type: **Android**.
3. Name: `SafeBand Android Client`.
4. Enter the verified Package Name:
   ```
   com.aistudio.safeband.xqmt
   ```
5. Enter your verified Debug SHA-1 Certificate Fingerprint:
   ```
   9C:BD:EB:7C:C6:26:AC:0E:3E:89:67:25:9D:16:10:80:FA:1A:7C:D7
   ```
6. Enter your verified Debug SHA-256 Certificate Fingerprint:
   ```
   51:19:1E:41:E6:BB:7F:A3:F8:B8:FB:B4:A7:BA:A6:99:B0:3D:B4:63:03:9C:34:96:AC:9D:71:3D:B6:84:65:84
   ```
7. Click **Create**.

> [!NOTE]
> If you create a custom release signing keystore, run `keytool -list -v -keystore <your-keystore.jks>` and add the release SHA-1 as a second Android Client ID.

---

## 3. End-to-End Verification

1. Start the laptop backend:
   ```cmd
   .\start-backend.bat
   ```
2. Open SafeBand Guardian on your Android phone.
3. In Mode Selection or First Launch, tap **Sign in with Google**.
4. The native Android Credential Manager account sheet will appear showing your Google accounts.
5. Select your account:
   - Android acquires the verified ID token.
   - Android dispatches `POST /api/v1/auth/google` with `{ "idToken": "..." }`.
   - Backend verifies token signature, expiry, and audience using Google's public key certificates.
   - Backend creates or updates the parent user and returns JWT access and refresh tokens.
   - Android unlocks the Parent Dashboard!
