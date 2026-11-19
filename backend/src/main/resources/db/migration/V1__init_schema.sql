-- SafeBand Guardian v4.0.0 Relational Schema (PostgreSQL & H2 Compatible)

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    photo_url VARCHAR(1024),
    google_sub VARCHAR(255) UNIQUE,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS child_profiles (
    id VARCHAR(36) PRIMARY KEY,
    parent_user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    child_name VARCHAR(100) NOT NULL,
    age VARCHAR(10),
    blood_type VARCHAR(10),
    primary_parent_phone VARCHAR(32),
    secondary_parent_phone VARCHAR(32),
    medical_notes TEXT,
    allergies TEXT,
    emergency_notes TEXT,
    photo_url VARCHAR(1024),
    paired_device_id VARCHAR(64),
    child_mode_phone_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    device_type VARCHAR(32) NOT NULL,
    hardware_node_type VARCHAR(64) NOT NULL,
    auth_token VARCHAR(255) NOT NULL,
    config_version INT NOT NULL DEFAULT 1,
    is_provisioned BOOLEAN NOT NULL DEFAULT FALSE,
    paired_child_id VARCHAR(36),
    owner_parent_id VARCHAR(36),
    last_heartbeat_at TIMESTAMP,
    tamper_status VARCHAR(32) DEFAULT 'PROTECTED',
    tamper_authorized_until TIMESTAMP,
    battery_percentage INT DEFAULT 100,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pairing_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    parent_user_id VARCHAR(36) NOT NULL,
    child_id VARCHAR(36),
    device_id VARCHAR(64),
    challenge_nonce VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS locations (
    id VARCHAR(36) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters FLOAT,
    sequence_number BIGINT NOT NULL,
    fix_timestamp TIMESTAMP NOT NULL,
    upload_timestamp TIMESTAMP NOT NULL,
    provider VARCHAR(32),
    battery_percentage INT,
    network_status VARCHAR(32),
    location_quality VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS geofence_zones (
    id VARCHAR(36) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_meters FLOAT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS incidents (
    incident_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    incident_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters FLOAT,
    address_text VARCHAR(512),
    start_timestamp TIMESTAMP NOT NULL,
    last_updated_timestamp TIMESTAMP NOT NULL,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_reason VARCHAR(512)
);

CREATE TABLE IF NOT EXISTS relay_observations (
    observation_id VARCHAR(64) PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL,
    relay_device_id VARCHAR(64) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters FLOAT,
    rssi INT,
    hop_count INT DEFAULT 1,
    observed_at TIMESTAMP NOT NULL,
    uploaded_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    incident_id VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id VARCHAR(64),
    ip_address VARCHAR(64),
    details TEXT,
    timestamp TIMESTAMP NOT NULL
);

-- Indexes for high-throughput queries
CREATE INDEX IF NOT EXISTS idx_locations_child_time ON locations(child_id, fix_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_child_status ON incidents(child_id, status);
CREATE INDEX IF NOT EXISTS idx_relay_obs_incident ON relay_observations(incident_id);
CREATE INDEX IF NOT EXISTS idx_devices_child ON devices(paired_child_id);
