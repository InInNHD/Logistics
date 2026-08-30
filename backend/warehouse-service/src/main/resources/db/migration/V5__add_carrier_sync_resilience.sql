ALTER TABLE wms_carrier_account
    ADD COLUMN sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sync_interval_minutes INT NOT NULL DEFAULT 30,
    ADD COLUMN next_sync_at DATETIME(6),
    ADD COLUMN lease_until DATETIME(6),
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0,
    ADD COLUMN circuit_opened_until DATETIME(6),
    ADD CONSTRAINT chk_carrier_sync_interval CHECK (sync_interval_minutes BETWEEN 1 AND 1440),
    ADD CONSTRAINT chk_carrier_failures CHECK (consecutive_failures >= 0);

CREATE INDEX idx_carrier_account_due
    ON wms_carrier_account (sync_enabled, status, next_sync_at, lease_until, circuit_opened_until);

UPDATE wms_carrier_order
SET recipient_region = '新疆 伊犁'
WHERE LOCATE(CONVERT(0xE8A5BFE8978F USING utf8mb4), recipient_region) > 0;

UPDATE wms_carrier_account
SET account_name = REPLACE(account_name, CONVERT(0xE8A5BFE8978F USING utf8mb4), '')
WHERE LOCATE(CONVERT(0xE8A5BFE8978F USING utf8mb4), account_name) > 0;
