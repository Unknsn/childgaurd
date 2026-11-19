# SafeBand Guardian v4.0.0 — Android Configuration Guide

## 1. Application Architecture & Namespace

* **Application ID**: `com.aistudio.safeband.xqmt`
* **Kotlin Namespace**: `com.example`
* **Minimum SDK**: 26 (Android 8.0 Oreo)
* **Target / Compile SDK**: 35 (Android 15)
* **Experience Mode**: Dual-role single APK. Selection is made during first launch or toggled in Settings.

---

## 2. Google OAuth2 Configuration

To enable Google Sign-In on the Android parent application:

### 2.1. Certificate Fingerprints
When registering the Android OAuth Client ID in Google Cloud Console, register the following SHA-1 and SHA-256 fingerprints from the development debug keystore:

* **Keystore Location**: `~/.android/debug.keystore`
* **Keystore Alias**: `androiddebugkey`
* **SHA-1 Fingerprint**:
  ```
  9C:BD:EB:7C:C6:26:AC:0E:3E:89:67:25:9D:16:10:80:FA:1A:7C:D7
  ```
* **SHA-256 Fingerprint**:
  ```
  51:19:1E:41:E6:BB:7F:A3:F8:B8:FB:B4:A7:BA:A6:99:B0:3D:B4:63:03:9C:34:96:AC:9D:71:3D:B6:84:65:84
  ```

### 2.2. Web Client ID
Set the OAuth Web Client ID in `app/src/main/res/values/strings.xml` or environment properties:
```xml
<string name="default_web_client_id">YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

---

## 3. Foreground Location Tracking Service

The child device experience relies on `ChildLocationForegroundService` to ensure continuous 1-minute location telemetry even when the phone screen is locked or the application is in the background.

### 3.1. Required Manifest Permissions
The service requires:
* `ACCESS_FINE_LOCATION`
* `ACCESS_COARSE_LOCATION`
* `ACCESS_BACKGROUND_LOCATION` (Android 10+)
* `FOREGROUND_SERVICE`
* `FOREGROUND_SERVICE_LOCATION` (Android 14+)
* `POST_NOTIFICATIONS` (Android 13+)

### 3.2. Battery Optimization Exemption
For uninterrupted emergency reporting on modern Android OS versions (MIUI, OneUI, Pixel):
1. Navigate to Android **App Info** -> **SafeBand Guardian**.
2. Tap **Battery**.
3. Select **Unrestricted** (disables aggressive OEM battery saver killing of foreground services).

---

## 4. BLE Hardware Configuration

* **Bluetooth Permissions**:
  * Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`.
  * Android 11 and below: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`.
* **Hardware Provisioning**: Ensure Location Services (GPS) are enabled on the smartphone; Android BLE scans cannot detect non-connected peripheral advertisements if system location is switched off.
