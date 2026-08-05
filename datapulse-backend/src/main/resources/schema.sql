-- Create users table
CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL
);

-- Create data_sources table
CREATE TABLE IF NOT EXISTS data_sources (
    source_id VARCHAR(50) PRIMARY KEY,
    source_name VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    owner VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create ingestion_jobs table
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    job_id VARCHAR(50) PRIMARY KEY,
    source_id VARCHAR(50) NOT NULL REFERENCES data_sources(source_id) ON DELETE CASCADE,
    records_received BIGINT NOT NULL DEFAULT 0,
    records_processed BIGINT NOT NULL DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL
);

-- Create validation_rules table
CREATE TABLE IF NOT EXISTS validation_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(50) NOT NULL REFERENCES data_sources(source_id) ON DELETE CASCADE,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL, -- e.g. REQUIRED_FIELD, INVALID_TYPE, INVALID_EMAIL, INVALID_PHONE, OUTLIER, DRIFT, CUSTOM_CHECK
    field_name VARCHAR(50) NOT NULL,
    threshold DOUBLE PRECISION,
    severity VARCHAR(20) NOT NULL, -- WARNING, ERROR, CRITICAL
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create quality_metrics table
CREATE TABLE IF NOT EXISTS quality_metrics (
    metric_id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(50) NOT NULL REFERENCES data_sources(source_id) ON DELETE CASCADE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    missing_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    duplicate_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    invalid_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    schema_changes INTEGER NOT NULL DEFAULT 0,
    outlier_count INTEGER NOT NULL DEFAULT 0,
    quality_score DOUBLE PRECISION NOT NULL DEFAULT 100.0
);

-- Create alerts table
CREATE TABLE IF NOT EXISTS alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(50) NOT NULL REFERENCES data_sources(source_id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);
