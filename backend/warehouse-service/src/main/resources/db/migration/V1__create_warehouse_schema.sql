CREATE TABLE wms_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(50) NOT NULL, name VARCHAR(100) NOT NULL,
    address VARCHAR(255), manager VARCHAR(100), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_warehouse_code UNIQUE (code)
);

CREATE TABLE wms_location (
    id BIGINT NOT NULL AUTO_INCREMENT, warehouse_id BIGINT NOT NULL, code VARCHAR(50) NOT NULL, name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'STORAGE', capacity BIGINT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_location_warehouse_code UNIQUE (warehouse_id, code),
    CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id)
);

CREATE TABLE wms_product (
    id BIGINT NOT NULL AUTO_INCREMENT, sku VARCHAR(80) NOT NULL, name VARCHAR(160) NOT NULL, category VARCHAR(100), unit VARCHAR(20) NOT NULL,
    barcode VARCHAR(100), safety_stock BIGINT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_product_sku UNIQUE (sku), CONSTRAINT uk_product_barcode UNIQUE (barcode)
);

CREATE TABLE wms_partner (
    id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(50) NOT NULL, name VARCHAR(160) NOT NULL, type VARCHAR(30) NOT NULL,
    contact VARCHAR(100), phone VARCHAR(50), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_partner_code UNIQUE (code)
);

CREATE TABLE wms_inbound_order (
    id BIGINT NOT NULL AUTO_INCREMENT, order_no VARCHAR(50) NOT NULL, supplier_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL, expected_at DATETIME(6), total_quantity BIGINT NOT NULL, received_quantity BIGINT NOT NULL,
    remark VARCHAR(500), received_at DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_inbound_order_no UNIQUE (order_no),
    CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_id) REFERENCES wms_partner(id), CONSTRAINT fk_inbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id)
);

CREATE TABLE wms_inbound_item (
    id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity BIGINT NOT NULL,
    received_quantity BIGINT NOT NULL, batch_no VARCHAR(100) NOT NULL DEFAULT '', expiry_date DATE,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_inbound_item_order FOREIGN KEY (order_id) REFERENCES wms_inbound_order(id), CONSTRAINT fk_inbound_item_product FOREIGN KEY (product_id) REFERENCES wms_product(id)
);

CREATE TABLE wms_inventory_balance (
    id BIGINT NOT NULL AUTO_INCREMENT, version BIGINT NOT NULL DEFAULT 0, warehouse_id BIGINT NOT NULL, location_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL, batch_no VARCHAR(100) NOT NULL DEFAULT '', expiry_date DATE, quantity BIGINT NOT NULL DEFAULT 0,
    allocated_quantity BIGINT NOT NULL DEFAULT 0, locked_quantity BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_inventory_dimension UNIQUE (warehouse_id, location_id, product_id, batch_no),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id), CONSTRAINT fk_inventory_location FOREIGN KEY (location_id) REFERENCES wms_location(id), CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES wms_product(id)
);

CREATE TABLE wms_inventory_movement (
    id BIGINT NOT NULL AUTO_INCREMENT, movement_no VARCHAR(60) NOT NULL, type VARCHAR(30) NOT NULL, warehouse_id BIGINT NOT NULL,
    location_id BIGINT, product_id BIGINT NOT NULL, batch_no VARCHAR(100) NOT NULL DEFAULT '', quantity BIGINT NOT NULL,
    reference_type VARCHAR(50), reference_id BIGINT, reason VARCHAR(500), operator_name VARCHAR(64),
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_movement_no UNIQUE (movement_no), INDEX idx_movement_created_at (created_at), INDEX idx_movement_reference (reference_type, reference_id)
);

CREATE TABLE wms_outbound_order (
    id BIGINT NOT NULL AUTO_INCREMENT, order_no VARCHAR(50) NOT NULL, customer_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL, required_at DATETIME(6), total_quantity BIGINT NOT NULL, allocated_quantity BIGINT NOT NULL,
    shipped_quantity BIGINT NOT NULL, remark VARCHAR(500), shipped_at DATETIME(6), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_outbound_order_no UNIQUE (order_no),
    CONSTRAINT fk_outbound_customer FOREIGN KEY (customer_id) REFERENCES wms_partner(id), CONSTRAINT fk_outbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse(id)
);

CREATE TABLE wms_outbound_item (
    id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity BIGINT NOT NULL,
    allocated_quantity BIGINT NOT NULL, shipped_quantity BIGINT NOT NULL, batch_no VARCHAR(100) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_outbound_item_order FOREIGN KEY (order_id) REFERENCES wms_outbound_order(id), CONSTRAINT fk_outbound_item_product FOREIGN KEY (product_id) REFERENCES wms_product(id)
);

CREATE TABLE wms_outbound_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL, inventory_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL, shipped BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id), INDEX idx_allocation_order (order_id),
    CONSTRAINT fk_allocation_order FOREIGN KEY (order_id) REFERENCES wms_outbound_order(id), CONSTRAINT fk_allocation_item FOREIGN KEY (order_item_id) REFERENCES wms_outbound_item(id), CONSTRAINT fk_allocation_inventory FOREIGN KEY (inventory_id) REFERENCES wms_inventory_balance(id)
);
