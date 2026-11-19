# SafeBand Guardian v4.0.0 — Emergency Relay Network Protocol

## 1. Architectural Mission

When a child is separated from their parent and out of range of their designated caregiver's smartphone, standard BLE cannot communicate directly to the cloud. The **SafeBand Community Emergency Relay Network** enables any participating Android device running SafeBand Guardian to act as an anonymous, zero-knowledge emergency data bridge.

---

## 2. Zero-PII Leakage Principles

The relay mechanism is engineered with strict cryptographic privacy boundaries:
1. **Zero Child Identification on Observer Phone**:
   * The observer application never sees the child's name, parent's contact info, or wearable serial number.
   * Advertisements only expose a one-way cryptographic hash: `SHA-256(deviceId + salt)`.
2. **Emergency-Only Ingestion**:
   * Normal periodic heartbeats (`0x00`) are ignored by community observer devices to protect battery and prevent passive location tracking of safe children.
   * Only beacons carrying `ALERT_SOS` (`0x02`) or `ALERT_TAMPER` (`0x03`) are ingested and forwarded.
3. **Observer Anonymity**:
   * Observer devices authenticate using an anonymous ephemeral observer token: `SHA-256(androidId + dailySalt)`.
   * The child's family cannot track or identify which specific community member provided the location fix.

---

## 3. Relay Ingestion Protocol

```
[Lost Child Wearable]               [Community Observer Phone]           [Laptop Backend]
        |                                       |                               |
        | 1. Broadcasts 0x5AFE Panic Beacon     |                               |
        |    (Alert: SOS, Hash: e4d901...)      |                               |
        |-------------------------------------->|                               |
        |                                       | 2. App extracts Beacon Hash   |
        |                                       | 3. Acquires High-Acc GPS Fix  |
        |                                       | 4. Packages Relay Request     |
        |                                       |                               |
        |                                       | 5. POST /api/v1/relay/observe |
        |                                       |    {observerTokenHash,        |
        |                                       |     anonymizedBeaconHash,     |
        |                                       |     latitude, longitude,      |
        |                                       |     accuracyMeters, rssi}     |
        |                                       |------------------------------>|
        |                                       |                               |
        |                                       | 6. Backend correlates hash    |
        |                                       |    to registered child device |
        |                                       | 7. Ingests breadcrumb &       |
        |                                       |    creates/updates Incident   |
        |                                       | 8. Dispatches Push & WebSocket|
        |                                       |    Alerts to Parent Phone     |
        |                                       |                               |
        |                                       | 9. Returns 200 OK             |
        |                                       |<------------------------------|
```

---

## 4. Backend Deduplication & Rate Limiting

To prevent denial-of-service or flood storms when a lost child is surrounded by multiple participating phones in a crowded area:
* **Time-Window Deduplication**: Observations matching the same `anonymizedBeaconHash` within a 15-second window are clustered.
* **Centroid Calculation**: If multiple observers report coordinates within the same window, the backend computes a weighted centroid based on inverse RSSI / accuracy values.
* **Observer Throttling**: A single observer token hash is limited to 10 observation submissions per minute.
