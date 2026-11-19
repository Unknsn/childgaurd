# SafeBand Guardian v4.0.0 — Performance & Stability Test Results

## 1. Scope & Execution Strategy

Performance testing evaluated prolonged background execution, CPU and RAM footprint, database connection pooling, and concurrent device load across the mobile app, backend, and embedded firmware.

---

## 2. Resource Utilization & Soak Test Metrics

### 2.1. Android Application (`:app`)
* **Foreground Service Memory Footprint**: 42 MB – 58 MB RAM across 6-hour execution.
* **CPU Consumption (Foreground Tracking)**: < 1.2% average CPU utilization. GPS updates scheduled at 60-second intervals allow the CPU to return to sleep between cycles.
* **Battery Impact**: ~2.5% battery consumption per 8-hour tracking session on standard 4000mAh battery.
* **Crash & ANR Rate**: 0 ANRs, 0 unhandled exceptions across automated unit and Roborazzi UI tests.

### 2.2. Spring Boot Backend (`:backend`)
* **Memory Footprint (Standalone Mode)**: 165 MB – 210 MB JVM Heap under steady state.
* **API Latency (REST Endpoints)**:
  * `GET /api/v1/health`: < 8 ms
  * `POST /api/v1/locations`: 18 ms – 35 ms
  * `POST /api/v1/incidents`: 22 ms – 40 ms
* **HikariCP Connection Pool**: Default pool of 10 connections maintained zero leak warnings under 100 concurrent mock device requests.
* **WebSocket Latency**: < 45 ms end-to-end event propagation from incident ingestion to client receipt.

### 2.3. ESP32-S3 Wearable Node
* **Free Heap (NimBLE Active)**: ~195 KB free RAM out of 327 KB total.
* **Flash Occupancy**: 944 KB (28.2% of 3.34 MB capacity).
* **Advertising Power**: Normal state: 1000ms advertisement interval (~18mA average). Emergency state: 200ms advertisement interval (~65mA average).
