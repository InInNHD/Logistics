CREATE TABLE wms_carrier_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    carrier_code VARCHAR(30) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    api_base_url VARCHAR(500) NOT NULL,
    credential_ciphertext VARCHAR(1000) NOT NULL,
    credential_hint VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    connection_status VARCHAR(20) NOT NULL DEFAULT 'UNTESTED',
    token_expires_at DATETIME(6),
    last_synced_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_carrier_account UNIQUE (warehouse_id, carrier_code, account_name),
    CONSTRAINT fk_carrier_account_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id),
    CONSTRAINT chk_carrier_account_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_carrier_connection_status CHECK (connection_status IN ('UNTESTED', 'AVAILABLE', 'FAILED'))
);

CREATE INDEX idx_carrier_account_search ON wms_carrier_account (carrier_code, status, connection_status);

CREATE TABLE wms_carrier_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    external_order_no VARCHAR(80) NOT NULL,
    tracking_no VARCHAR(80),
    recipient_region VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    placed_at DATETIME(6),
    synced_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_carrier_external_order UNIQUE (account_id, external_order_no),
    CONSTRAINT fk_carrier_order_account FOREIGN KEY (account_id) REFERENCES wms_carrier_account(id),
    CONSTRAINT chk_carrier_order_amount CHECK (amount >= 0)
);

CREATE INDEX idx_carrier_order_status_sync ON wms_carrier_order (status, synced_at);

CREATE TABLE wms_carrier_sync_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fetched_count INT NOT NULL DEFAULT 0,
    message VARCHAR(500),
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_carrier_sync_log_account FOREIGN KEY (account_id) REFERENCES wms_carrier_account(id),
    CONSTRAINT chk_carrier_sync_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT chk_carrier_sync_count CHECK (fetched_count >= 0)
);

CREATE INDEX idx_carrier_sync_log_account_time ON wms_carrier_sync_log (account_id, started_at);
