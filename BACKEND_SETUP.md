# SafeBand Guardian v4.0.0 — Backend Setup Guide

## 1. Prerequisites

To run the SafeBand Guardian backend on a developer laptop:
* **Operating System**: Windows 10/11, macOS, or Linux.
* **Java Development Kit**: JDK 21 (Eclipse Adoptium Temurin or OpenJDK).
* **Git**: Installed and available on system PATH.
* **Network**: Active internet connection (required for Gradle dependency resolution and Cloudflare public tunnel).

---

## 2. Quick Start: One-Click Windows Automation

SafeBand Guardian includes automated scripts to start, check, and stop the entire backend stack with zero manual configuration.

### 2.1. Start Backend & Public Tunnel
Double-click `start-backend.bat` or run in PowerShell:
```powershell
.\start-backend.bat
```
What this automated script does:
1. Validates that Java 21 is installed.
2. Creates the local database storage directory (`backend/data/`).
3. Compiles and launches the Spring Boot backend on `http://localhost:8080` in persistent H2 standalone mode.
4. Polls `http://localhost:8080/api/v1/health` until the service responds healthy (`UP`).
5. Checks for `cloudflared.exe` (downloads it automatically if absent).
6. Starts an outbound Cloudflare Tunnel routing to `localhost:8080`.
7. Displays your live public tunnel URL (e.g., `https://safeband-xyz.trycloudflare.com`).

### 2.2. Verify Service Status
To inspect running backend and tunnel processes:
```powershell
.\backend-status.bat
```
Outputs:
* Spring Boot Backend: Running (PID: xxxx), Port: 8080, Health: UP
* Cloudflare Tunnel: Running (PID: yyyy), Public URL: `https://...`

### 2.3. Graceful Shutdown
To cleanly stop both backend and tunnel:
```powershell
.\stop-backend.bat
```

---

## 3. Configuration Profiles

Configuration is managed via `backend/src/main/resources/application.yml` and environment variables.

### 3.1. Standalone Profile (Default)
Ideal for laptop development with zero external dependencies:
* Database: Embedded H2 file database at `backend/data/safeband.mv.db`
* Dialect: PostgreSQL compatibility mode with Flyway schema migration
* Memory footprint: ~180MB RAM

### 3.2. Production PostgreSQL Profile
For multi-node cloud deployments:
```bash
export SPRING_PROFILES_ACTIVE=postgres
export DB_HOST=db.example.com
export DB_PORT=5432
export DB_NAME=safeband
export DB_USER=safeband_user
export DB_PASSWORD=your_secure_password
```

---

## 4. Manual Execution via Gradle

If you prefer building and running directly via Gradle:
```powershell
# Build executable jar
.\gradlew :backend:bootJar

# Run backend
.\gradlew :backend:bootRun
```
Access Swagger/Health endpoint: `http://localhost:8080/api/v1/health`
