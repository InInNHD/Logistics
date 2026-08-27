CREATE TABLE wms_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_body LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_operation_key UNIQUE (operation, idempotency_key),
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_location_warehouse_status ON wms_location (warehouse_id, status);
CREATE INDEX idx_product_status ON wms_product (status);
CREATE INDEX idx_partner_type_status ON wms_partner (type, status);
CREATE INDEX idx_inbound_status_created ON wms_inbound_order (status, created_at);
CREATE INDEX idx_outbound_status_created ON wms_outbound_order (status, created_at);
CREATE INDEX idx_inventory_fefo ON wms_inventory_balance (warehouse_id, product_id, expiry_date, batch_no);
CREATE INDEX idx_movement_wh_type_created ON wms_inventory_movement (warehouse_id, type, created_at);
CREATE INDEX idx_idempotency_created ON wms_idempotency_record (created_at);

ALTER TABLE wms_inventory_movement
    ADD CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id),
    ADD CONSTRAINT fk_movement_location FOREIGN KEY (location_id) REFERENCES wms_location(id),
    ADD CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES wms_product(id);

ALTER TABLE wms_location
    ADD CONSTRAINT chk_location_capacity CHECK (capacity >= 0),
    ADD CONSTRAINT chk_location_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE wms_product
    ADD CONSTRAINT chk_product_safety_stock CHECK (safety_stock >= 0),
    ADD CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE wms_warehouse
    ADD CONSTRAINT chk_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE wms_partner
    ADD CONSTRAINT chk_partner_type CHECK (type IN ('SUPPLIER', 'CUSTOMER')),
    ADD CONSTRAINT chk_partner_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE wms_inbound_order
    ADD CONSTRAINT chk_inbound_quantities CHECK (
        total_quantity > 0 AND received_quantity >= 0 AND received_quantity <= total_quantity
    );

ALTER TABLE wms_inbound_item
    ADD CONSTRAINT chk_inbound_item_quantities CHECK (
        quantity > 0 AND received_quantity >= 0 AND received_quantity <= quantity
    );

ALTER TABLE wms_inventory_balance
    ADD CONSTRAINT chk_inventory_quantities CHECK (
        quantity >= 0 AND allocated_quantity >= 0 AND locked_quantity >= 0
        AND allocated_quantity + locked_quantity <= quantity
    );

ALTER TABLE wms_outbound_order
    ADD CONSTRAINT chk_outbound_quantities CHECK (
        total_quantity > 0 AND allocated_quantity >= 0 AND shipped_quantity >= 0
        AND allocated_quantity <= total_quantity AND shipped_quantity <= allocated_quantity
    );

ALTER TABLE wms_outbound_item
    ADD CONSTRAINT chk_outbound_item_quantities CHECK (
        quantity > 0 AND allocated_quantity >= 0 AND shipped_quantity >= 0
        AND allocated_quantity <= quantity AND shipped_quantity <= allocated_quantity
    );

ALTER TABLE wms_outbound_allocation
    ADD CONSTRAINT chk_allocation_quantity CHECK (quantity > 0);
