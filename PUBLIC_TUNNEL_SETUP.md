# SafeBand Guardian v4.0.0 — Public Tunnel Setup Guide

## 1. Overview & Architectural Purpose

In a real-world child safety deployment, parents and children are rarely on the same Wi-Fi network. The child may be at school or in transit using cellular LTE/5G, while the parent is at an office or mobile.

Traditional local development requires complex router port-forwarding, static public IP leases, or fragile dynamic DNS. **SafeBand Guardian v4.0.0 solves this natively** by integrating an automated, secure outbound tunnel using Cloudflare Tunnel (`cloudflared`).

```
+------------------+         +-------------------------------+         +-----------------------+
|  Child or Parent |  HTTPS  | Cloudflare Edge Network       | Encrypt | Laptop Backend        |
|  Android Device  +-------->| (*.trycloudflare.com / domain)+========>| localhost:8080        |
|  (Cellular / 4G) |         | TLS 1.3 Termination           | Tunnel  | Spring Boot / H2      |
+------------------+         +-------------------------------+         +-----------------------+
```

### Key Advantages:
1. **Zero Port Forwarding**: No inbound ports are opened on your home or office router.
2. **Global Reach**: Operates across carrier-grade NAT (CGNAT), corporate firewalls, and mobile data networks.
3. **End-to-End TLS**: All traffic is encrypted in transit using industry-standard TLS 1.3 certificates managed by Cloudflare.
4. **Instant Spin-up**: Zero account registration or credit card required when using free Quick Tunnels (`trycloudflare.com`).

---

## 2. Automated Windows Tunnel Orchestration

The project repository includes complete automation scripts:

* `start-backend.bat` (and `.ps1`):
  * Launches the Spring Boot backend on `http://localhost:8080`.
  * Waits for `http://localhost:8080/api/v1/health` to return HTTP 200 `UP`.
  * Verifies if `cloudflared.exe` is present in the working directory; if missing, automatically downloads the official release from Cloudflare.
  * Starts the tunnel with `cloudflared tunnel --url http://localhost:8080`.
  * Parses the dynamically assigned public HTTPS URL and prints it clearly for the user.
* `backend-status.bat`: Checks if backend and tunnel processes are healthy.
* `stop-backend.bat`: Cleanly kills the background PIDs.

---

## 3. Configuring the Android Application

Once the public tunnel URL is generated:

1. Open the SafeBand Guardian application on the Parent or Child phone.
2. Navigate to **Settings** (Gear icon in top bar or navigation drawer).
3. Scroll to the **Backend & Cloud Tunnel** card.
4. Enter the public tunnel URL into the **Backend Base URL** input field (e.g. `https://my-tunnel.trycloudflare.com`).
5. Tap **Test Connection & Save**:
   * The app sends a `GET /api/v1/health` ping.
   * On HTTP 200 with status `UP`, a green confirmation checkmark appears, and the endpoint is permanently saved in EncryptedSharedPreferences.
6. The app is now connected to your laptop backend over public mobile internet!

---

## 4. Custom Named Domain Setup (Optional Production Mode)

If you own a custom domain (e.g., `api.safeband-guardian.com`) and manage its DNS on Cloudflare:

1. Authenticate `cloudflared`:
   ```bash
   cloudflared tunnel login
   ```
2. Create a persistent named tunnel:
   ```bash
   cloudflared tunnel create safeband-prod
   ```
3. Route DNS to your tunnel:
   ```bash
   cloudflared tunnel route dns safeband-prod api.safeband-guardian.com
   ```
4. Create `backend/tunnel-config.yml`:
   ```yaml
   tunnel: <TUNNEL_UUID>
   credentials-file: C:\Users\<USER>\.cloudflared\<TUNNEL_UUID>.json

   ingress:
     - hostname: api.safeband-guardian.com
       service: http://localhost:8080
     - service: http_status:404
   ```
5. Run the named tunnel:
   ```bash
   cloudflared tunnel --config backend/tunnel-config.yml run
   ```
