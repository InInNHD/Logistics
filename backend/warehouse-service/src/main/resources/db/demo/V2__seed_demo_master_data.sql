INSERT INTO wms_warehouse (id, code, name, address, manager, status, created_at, updated_at) VALUES
(1, 'WH-SH-01', '上海中心仓', '上海市嘉定区嘉松北路 888 号', '张伟', 'ACTIVE', NOW(6), NOW(6)),
(2, 'WH-SZ-01', '深圳南方仓', '深圳市龙岗区平湖物流园', '李娜', 'ACTIVE', NOW(6), NOW(6));

INSERT INTO wms_location (id, warehouse_id, code, name, type, capacity, status, created_at, updated_at) VALUES
(1, 1, 'REC-01', '收货暂存区', 'RECEIVING', 10000, 'ACTIVE', NOW(6), NOW(6)),
(2, 1, 'A-01-01', 'A区一排一层', 'STORAGE', 5000, 'ACTIVE', NOW(6), NOW(6)),
(3, 1, 'SHIP-01', '发货暂存区', 'SHIPPING', 10000, 'ACTIVE', NOW(6), NOW(6)),
(4, 2, 'REC-01', '收货暂存区', 'RECEIVING', 8000, 'ACTIVE', NOW(6), NOW(6)),
(5, 2, 'B-01-01', 'B区一排一层', 'STORAGE', 4000, 'ACTIVE', NOW(6), NOW(6));

INSERT INTO wms_product (id, sku, name, category, unit, barcode, safety_stock, status, created_at, updated_at) VALUES
(1, 'FF-BOX-001', '标准物流周转箱', '仓储耗材', '个', '6970000000011', 50, 'ACTIVE', NOW(6), NOW(6)),
(2, 'FF-TAPE-001', '高粘封箱胶带', '仓储耗材', '卷', '6970000000028', 100, 'ACTIVE', NOW(6), NOW(6)),
(3, 'FF-SCAN-001', '工业无线扫码枪', '仓储设备', '台', '6970000000035', 10, 'ACTIVE', NOW(6), NOW(6));

INSERT INTO wms_partner (id, code, name, type, contact, phone, status, created_at, updated_at) VALUES
(1, 'SUP-001', '萤火供应链有限公司', 'SUPPLIER', '王经理', '13800000001', 'ACTIVE', NOW(6), NOW(6)),
(2, 'CUS-001', '星河零售有限公司', 'CUSTOMER', '陈经理', '13800000002', 'ACTIVE', NOW(6), NOW(6));

INSERT INTO wms_inventory_balance (id, version, warehouse_id, location_id, product_id, batch_no, expiry_date, quantity, allocated_quantity, locked_quantity, created_at, updated_at) VALUES
(1, 0, 1, 2, 1, 'DEMO-202608', NULL, 300, 0, 0, NOW(6), NOW(6)),
(2, 0, 1, 2, 2, 'DEMO-202608', NULL, 500, 0, 0, NOW(6), NOW(6)),
(3, 0, 2, 5, 3, 'DEMO-202608', NULL, 20, 0, 0, NOW(6), NOW(6));
