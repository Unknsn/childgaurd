# SafeBand Guardian v4.0.0 — Troubleshooting & Diagnostics Guide

## 1. Backend & Server Diagnostics

### 1.1. Port 8080 Already in Use
* **Symptom**: `Web server failed to start. Port 8080 was already in use.`
* **Diagnosis**: An orphaned Java process or local development server is holding the port.
* **Resolution**:
  Run in PowerShell:
  ```powershell
  Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
  ```
  Or simply execute:
  ```powershell
  .\stop-backend.bat
  ```

### 1.2. H2 Database Lock Error
* **Symptom**: `Database may be already in use: "Locked by another process"`
* **Diagnosis**: Multiple Spring Boot instances are trying to open `./data/safeband.mv.db`.
* **Resolution**: H2 is configured with `AUTO_SERVER=TRUE` to allow multiple connections; ensure any stale test processes are terminated:
  ```powershell
  Stop-Process -Name java -Force
  ```

---

## 2. Cloudflare Tunnel Diagnostics

### 2.1. Tunnel Process Fails to Start
* **Symptom**: `cloudflared.exe` is not recognized or returns exit code 1.
* **Diagnosis**: Antivirus software blocked dynamic binary execution, or network firewall blocked DNS-over-HTTPS.
* **Resolution**: Run `cloudflared --version` manually in PowerShell. If missing, `start-backend.bat` downloads it from GitHub releases. Add `cloudflared.exe` to Windows Defender exclusions if blocked.

### 2.2. Tunnel URL Inaccessible on Phone
* **Symptom**: Android app displays `Connection Failed: Timeout` when testing URL.
* **Diagnosis**:
  1. Verify the URL starts with `https://` (Cloudflare enforces TLS).
  2. Verify the tunnel is still active in `backend-status.bat`. Quick tunnels change URLs if restarted.

---

## 3. BLE & Hardware Diagnostics (ESP32-S3)

### 3.1. False Tamper Alarms (GPIO 3 / GPIO 4)
* **Symptom**: Wearable alerts `TAMPER_DETECTED` even when strap is physically intact.
* **Diagnosis**: High parasitic capacitance on thin ribbon traces prevents GPIO 4 from rising within a fast test pulse.
* **Resolution**: v4.0.0 increased the test settling pulse to 200µs (`delayMicroseconds(200)`). Ensure GPIO 3 and GPIO 4 wiring is secure and not touching ground pins.

### 3.2. Pairing Handshake Times Out
* **Symptom**: App reports `Pairing failed: Device did not respond`.
* **Diagnosis**:
  1. Bluetooth location permissions denied on Android device.
  2. ESP32 node is already paired with another child ID.
* **Resolution**: Hold physical button on ESP32 for 15 seconds during power-up to clear NVS flash and reset pairing state.

---

## 4. Android App Diagnostics

### 4.1. Location Tracking Stops When App Closes
* **Symptom**: Breadcrumb updates stop after 5-10 minutes of screen-off time.
* **Diagnosis**: OEM battery optimization killed the foreground service.
* **Resolution**: Set Battery usage to **Unrestricted** in Android Settings -> Apps -> SafeBand Guardian. Ensure the persistent notification "SafeBand Child Protection Active" is visible in the status bar.

### 4.2. Google Sign-In Fails (Error Code 10 / 12500)
* **Symptom**: Tap "Sign in with Google" prompts account picker but immediately fails with code 10.
* **Diagnosis**: SHA-1 fingerprint mismatch in Google Cloud Console.
* **Resolution**: Ensure the exact SHA-1 fingerprint from `GOOGLE_AUTH_SETUP.md` (`9C:BD:EB:7C:C6:26:AC:0E:3E:89:67:25:9D:16:10:80:FA:1A:7C:D7`) is added to the Android OAuth 2.0 Client ID in your Google Cloud Console.
