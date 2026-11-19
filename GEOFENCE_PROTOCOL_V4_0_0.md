# SafeBand Guardian v4.0.0 — Geofencing & Boundary Evaluation Protocol

## 1. Overview

The SafeBand Guardian geofencing engine safeguards children by continuously monitoring spatial coordinates against parent-defined safe perimeters (such as home, school, parks, or neighborhoods). The evaluation operates cooperatively across both client (Android app) and server (Spring Boot backend).

---

## 2. Mathematical Foundation

### 2.1. Haversine Great-Circle Distance
For circular safe zones, the radial distance $d$ between the child's coordinate $(\phi_1, \lambda_1)$ and the zone center $(\phi_2, \lambda_2)$ is computed using the Haversine equation:

$$\Delta\phi = \phi_2 - \phi_1$$
$$\Delta\lambda = \lambda_2 - \lambda_1$$
$$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)$$
$$c = 2 \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1-a}\right)$$
$$d = R \cdot c$$

Where:
* $R = 6,371,000 \text{ meters}$ (Mean Earth radius)
* Coordinates are in radians.

### 2.2. Ray-Casting Algorithm for Arbitrary Polygons
For complex neighborhood boundaries defined via GeoJSON polygons, the Point-in-Polygon (PIP) ray-casting algorithm tests whether a horizontal ray extending eastward from the child's coordinate intersects an odd number of polygon boundary segments.

---

## 3. False Alarm Mitigation: Hysteresis & Dwell Filtering

Raw GPS measurements are inherently susceptible to multipath reflection, atmospheric distortion, and indoor positioning scatter. To prevent false breach alarms:

1. **Boundary Hysteresis Buffer ($H = 15 \text{ meters}$)**:
   * **Breach Trigger**: Triggered only when $d > (\text{radius} + \text{accuracy} + H)$.
   * **Safe Restoration**: Marked restored only when $d < (\text{radius} - H)$.
2. **Dwell Time Window ($T_{\text{dwell}} = 60 \text{ seconds}$)**:
   * A single anomalous GPS breadcrumb outside the perimeter does not trigger an immediate siren alert.
   * The backend requires at least two consecutive out-of-bounds breadcrumbs spanning at least 60 seconds before escalating to a critical `GEOFENCE_BREACH` incident.

---

## 4. Dual-Layer Evaluation Architecture

* **Client-Side (Android)**: Evaluates cached active geofences locally inside `ChildLocationForegroundService` every 60 seconds, enabling immediate local notifications even during transient cellular disconnections.
* **Server-Side (Backend)**: Re-evaluates every ingested location breadcrumb in `GeofenceService.evaluateGeofences()`. If an active breach is confirmed, the backend automatically creates an `IncidentReportEntity` and broadcasts high-priority push notifications and WebSocket alerts to all registered parent guardians.
