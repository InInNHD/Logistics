-- 真实匿名交易样本：UCI Online Retail II（CC BY 4.0）
-- https://archive.ics.uci.edu/dataset/502/online%2Bretail
-- DOI: 10.24432/C5CG6D
-- 仅平移演示时间并分配 WMS 作业状态；发票号、商品、数量、原始时间、国家和金额均来自源数据。

SET @uci_demo_order_lines = JSON_ARRAY(
JSON_ARRAY('536365', '2010-12-01 08:26:00', 'United Kingdom', 'UCI-GB', 'UCI 英国匿名批发客户', '85123A', 'WHITE HANGING HEART T-LIGHT HOLDER', 6, 2.55, 'SHIPPED', 0),
JSON_ARRAY('536365', '2010-12-01 08:26:00', 'United Kingdom', 'UCI-GB', 'UCI 英国匿名批发客户', '71053', 'WHITE METAL LANTERN', 6, 3.39, 'SHIPPED', 0),
JSON_ARRAY('536365', '2010-12-01 08:26:00', 'United Kingdom', 'UCI-GB', 'UCI 英国匿名批发客户', '84406B', 'CREAM CUPID HEARTS COAT HANGER', 8, 2.75, 'SHIPPED', 0),
JSON_ARRAY('536365', '2010-12-01 08:26:00', 'United Kingdom', 'UCI-GB', 'UCI 英国匿名批发客户', '84029G', 'KNITTED UNION FLAG HOT WATER BOTTLE', 6, 3.39, 'SHIPPED', 0),
JSON_ARRAY('536365', '2010-12-01 08:26:00', 'United Kingdom', 'UCI-GB', 'UCI 英国匿名批发客户', '84029E', 'RED WOOLLY HOTTIE WHITE HEART.', 6, 3.39, 'SHIPPED', 0),
JSON_ARRAY('536370', '2010-12-01 08:45:00', 'France', 'UCI-FR', 'UCI 法国匿名批发客户', '22728', 'ALARM CLOCK BAKELIKE PINK', 24, 3.75, 'SHIPPED', 1),
JSON_ARRAY('536370', '2010-12-01 08:45:00', 'France', 'UCI-FR', 'UCI 法国匿名批发客户', '22727', 'ALARM CLOCK BAKELIKE RED', 24, 3.75, 'SHIPPED', 1),
JSON_ARRAY('536370', '2010-12-01 08:45:00', 'France', 'UCI-FR', 'UCI 法国匿名批发客户', '22726', 'ALARM CLOCK BAKELIKE GREEN', 12, 3.75, 'SHIPPED', 1),
JSON_ARRAY('536370', '2010-12-01 08:45:00', 'France', 'UCI-FR', 'UCI 法国匿名批发客户', '21724', 'PANDA AND BUNNIES STICKER SHEET', 12, 0.85, 'SHIPPED', 1),
JSON_ARRAY('536370', '2010-12-01 08:45:00', 'France', 'UCI-FR', 'UCI 法国匿名批发客户', '21883', 'STARS GIFT TAPE', 24, 0.65, 'SHIPPED', 1),
JSON_ARRAY('536527', '2010-12-01 13:04:00', 'Germany', 'UCI-DE', 'UCI 德国匿名批发客户', '22809', 'SET OF 6 T-LIGHTS SANTA', 6, 2.95, 'PACKED', 2),
JSON_ARRAY('536527', '2010-12-01 13:04:00', 'Germany', 'UCI-DE', 'UCI 德国匿名批发客户', '84347', 'ROTATING SILVER ANGELS T-LIGHT HLDR', 6, 2.55, 'PACKED', 2),
JSON_ARRAY('536527', '2010-12-01 13:04:00', 'Germany', 'UCI-DE', 'UCI 德国匿名批发客户', '84945', 'MULTI COLOUR SILVER T-LIGHT HOLDER', 12, 0.85, 'PACKED', 2),
JSON_ARRAY('536527', '2010-12-01 13:04:00', 'Germany', 'UCI-DE', 'UCI 德国匿名批发客户', '22242', '5 HOOK HANGER MAGIC TOADSTOOL', 12, 1.65, 'PACKED', 2),
JSON_ARRAY('536527', '2010-12-01 13:04:00', 'Germany', 'UCI-DE', 'UCI 德国匿名批发客户', '22244', '3 HOOK HANGER MAGIC GARDEN', 12, 1.95, 'PACKED', 2),
JSON_ARRAY('539491', '2010-12-20 10:09:00', 'Netherlands', 'UCI-NL', 'UCI 荷兰匿名批发客户', '21981', 'PACK OF 12 WOODLAND TISSUES', 12, 0.29, 'PICKED', 3),
JSON_ARRAY('539491', '2010-12-20 10:09:00', 'Netherlands', 'UCI-NL', 'UCI 荷兰匿名批发客户', '21986', 'PACK OF 12 PINK POLKADOT TISSUES', 12, 0.29, 'PICKED', 3),
JSON_ARRAY('539491', '2010-12-20 10:09:00', 'Netherlands', 'UCI-NL', 'UCI 荷兰匿名批发客户', '22720', 'SET OF 3 CAKE TINS PANTRY DESIGN', 2, 4.95, 'PICKED', 3),
JSON_ARRAY('539491', '2010-12-20 10:09:00', 'Netherlands', 'UCI-NL', 'UCI 荷兰匿名批发客户', '21931', 'JUMBO STORAGE BAG SUKI', 1, 1.95, 'PICKED', 3),
JSON_ARRAY('539491', '2010-12-20 10:09:00', 'Netherlands', 'UCI-NL', 'UCI 荷兰匿名批发客户', '22613', 'PACK OF 20 SPACEBOY NAPKINS', 2, 0.85, 'PICKED', 3),
JSON_ARRAY('536389', '2010-12-01 10:03:00', 'Australia', 'UCI-AU', 'UCI 澳大利亚匿名批发客户', '22941', 'CHRISTMAS LIGHTS 10 REINDEER', 6, 8.50, 'ALLOCATED', 4),
JSON_ARRAY('536389', '2010-12-01 10:03:00', 'Australia', 'UCI-AU', 'UCI 澳大利亚匿名批发客户', '21622', 'VINTAGE UNION JACK CUSHION COVER', 8, 4.95, 'ALLOCATED', 4),
JSON_ARRAY('536389', '2010-12-01 10:03:00', 'Australia', 'UCI-AU', 'UCI 澳大利亚匿名批发客户', '21791', 'VINTAGE HEADS AND TAILS CARD GAME', 12, 1.25, 'ALLOCATED', 4),
JSON_ARRAY('536389', '2010-12-01 10:03:00', 'Australia', 'UCI-AU', 'UCI 澳大利亚匿名批发客户', '35004C', 'SET OF 3 COLOURED FLYING DUCKS', 6, 5.45, 'ALLOCATED', 4),
JSON_ARRAY('536389', '2010-12-01 10:03:00', 'Australia', 'UCI-AU', 'UCI 澳大利亚匿名批发客户', '35004G', 'SET OF 3 GOLD FLYING DUCKS', 4, 6.35, 'ALLOCATED', 4),
JSON_ARRAY('538095', '2010-12-09 14:55:00', 'Spain', 'UCI-ES', 'UCI 西班牙匿名批发客户', '22504', 'CABIN BAG VINTAGE RETROSPOT', 1, 29.95, 'PENDING', 5),
JSON_ARRAY('538095', '2010-12-09 14:55:00', 'Spain', 'UCI-ES', 'UCI 西班牙匿名批发客户', '22212', 'FOUR HOOK WHITE LOVEBIRDS', 1, 2.10, 'PENDING', 5),
JSON_ARRAY('538095', '2010-12-09 14:55:00', 'Spain', 'UCI-ES', 'UCI 西班牙匿名批发客户', '22953', 'BIRTHDAY PARTY CORDON BARRIER TAPE', 1, 1.25, 'PENDING', 5),
JSON_ARRAY('538095', '2010-12-09 14:55:00', 'Spain', 'UCI-ES', 'UCI 西班牙匿名批发客户', '84818', 'DANISH ROSE PHOTO FRAME', 1, 2.55, 'PENDING', 5),
JSON_ARRAY('538095', '2010-12-09 14:55:00', 'Spain', 'UCI-ES', 'UCI 西班牙匿名批发客户', '21114', 'LAVENDER SCENTED FABRIC HEART', 5, 1.25, 'PENDING', 5),
JSON_ARRAY('536858', '2010-12-03 10:36:00', 'Switzerland', 'UCI-CH', 'UCI 瑞士匿名批发客户', '22326', 'ROUND SNACK BOXES SET OF4 WOODLAND', 30, 2.95, 'CANCELLED', 6),
JSON_ARRAY('536858', '2010-12-03 10:36:00', 'Switzerland', 'UCI-CH', 'UCI 瑞士匿名批发客户', '22554', 'PLASTERS IN TIN WOODLAND ANIMALS', 36, 1.65, 'CANCELLED', 6),
JSON_ARRAY('536858', '2010-12-03 10:36:00', 'Switzerland', 'UCI-CH', 'UCI 瑞士匿名批发客户', '21731', 'RED TOADSTOOL LED NIGHT LIGHT', 24, 1.65, 'CANCELLED', 6),
JSON_ARRAY('536858', '2010-12-03 10:36:00', 'Switzerland', 'UCI-CH', 'UCI 瑞士匿名批发客户', '20677', 'PINK POLKADOT BOWL', 16, 1.25, 'CANCELLED', 6),
JSON_ARRAY('536858', '2010-12-03 10:36:00', 'Switzerland', 'UCI-CH', 'UCI 瑞士匿名批发客户', '20750', 'RED RETROSPOT MINI CASES', 2, 7.95, 'CANCELLED', 6),
JSON_ARRAY('536990', '2010-12-03 15:14:00', 'Portugal', 'UCI-PT', 'UCI 葡萄牙匿名批发客户', '21992', 'VINTAGE PAISLEY STATIONERY SET', 6, 2.95, 'RETURNED', 6),
JSON_ARRAY('536990', '2010-12-03 15:14:00', 'Portugal', 'UCI-PT', 'UCI 葡萄牙匿名批发客户', '22383', 'LUNCH BAG SUKI DESIGN', 10, 1.65, 'RETURNED', 6),
JSON_ARRAY('536990', '2010-12-03 15:14:00', 'Portugal', 'UCI-PT', 'UCI 葡萄牙匿名批发客户', '20728', 'LUNCH BAG CARS BLUE', 14, 1.65, 'RETURNED', 6),
JSON_ARRAY('536990', '2010-12-03 15:14:00', 'Portugal', 'UCI-PT', 'UCI 葡萄牙匿名批发客户', '20658', 'RED RETROSPOT LUGGAGE TAG', 12, 1.25, 'RETURNED', 6),
JSON_ARRAY('536990', '2010-12-03 15:14:00', 'Portugal', 'UCI-PT', 'UCI 葡萄牙匿名批发客户', '20669', 'RED HEART LUGGAGE TAG', 12, 1.25, 'RETURNED', 6)
);

-- 国内快递公司作为物流服务供应商；电话均来自各公司官网全国客服热线。
INSERT IGNORE INTO wms_partner (code, name, type, contact, phone, status, created_at, updated_at) VALUES
('CAR-ZTO', '中通快递', 'SUPPLIER', '全国统一客服', '95311', 'ACTIVE', NOW(6), NOW(6)),
('CAR-YTO', '圆通速递', 'SUPPLIER', '全国统一客服', '95554', 'ACTIVE', NOW(6), NOW(6)),
('CAR-YUNDA', '韵达速递', 'SUPPLIER', '全国统一客服', '95546', 'ACTIVE', NOW(6), NOW(6)),
('CAR-STO', '申通快递', 'SUPPLIER', '全国统一客服', '95543', 'ACTIVE', NOW(6), NOW(6)),
('CAR-SF', '顺丰速运', 'SUPPLIER', '全国统一客服', '95338', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO wms_partner (code, name, type, contact, phone, status, created_at, updated_at)
SELECT DISTINCT src.customer_code, src.customer_name, 'CUSTOMER', '公开数据匿名客户', NULL, 'ACTIVE', NOW(6), NOW(6)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src;

INSERT IGNORE INTO wms_product (sku, name, category, unit, barcode, safety_stock, status, created_at, updated_at)
SELECT DISTINCT CONCAT('UCI-', src.stock_code), src.description, 'UCI 真实零售礼品', '件', NULL, 10, 'ACTIVE', NOW(6), NOW(6)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src;

INSERT IGNORE INTO wms_inventory_balance
    (version, warehouse_id, location_id, product_id, batch_no, expiry_date, quantity, allocated_quantity, locked_quantity, created_at, updated_at)
SELECT 0, w.id, loc.id, p.id, 'UCI-PUBLIC-2010', NULL, 500,
       SUM(CASE WHEN src.order_status IN ('ALLOCATED', 'PICKED', 'PACKED') THEN src.quantity ELSE 0 END),
       0, NOW(6), NOW(6)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src
JOIN wms_product p ON p.sku = CONCAT('UCI-', src.stock_code)
JOIN wms_warehouse w ON w.code = 'WH-SH-01'
JOIN wms_location loc ON loc.warehouse_id = w.id AND loc.code = 'A-01-01'
GROUP BY w.id, loc.id, p.id;

INSERT IGNORE INTO wms_outbound_order
    (order_no, customer_id, warehouse_id, status, required_at, total_quantity, allocated_quantity,
     shipped_quantity, remark, shipped_at, created_at, updated_at)
SELECT CONCAT('UCI-', src.invoice_no), customer.id, warehouse.id, src.order_status,
       DATE_ADD(NOW(6), INTERVAL (7 - src.day_offset) DAY), SUM(src.quantity),
       CASE WHEN src.order_status IN ('ALLOCATED', 'PICKED', 'PACKED', 'SHIPPED', 'RETURNED') THEN SUM(src.quantity) ELSE 0 END,
       CASE WHEN src.order_status IN ('SHIPPED', 'RETURNED') THEN SUM(src.quantity) ELSE 0 END,
       CONCAT('UCI Online Retail II 真实匿名交易；原始时间 ', DATE_FORMAT(MIN(src.source_date), '%Y-%m-%d %H:%i'),
              '；国家 ', src.country, '；样本金额 GBP ', FORMAT(SUM(src.quantity * src.unit_price_gbp), 2), '；演示时间已平移'),
       CASE WHEN src.order_status IN ('SHIPPED', 'RETURNED') THEN DATE_SUB(NOW(6), INTERVAL src.day_offset DAY) ELSE NULL END,
       DATE_SUB(NOW(6), INTERVAL src.day_offset DAY), DATE_SUB(NOW(6), INTERVAL src.day_offset DAY)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src
JOIN wms_partner customer ON customer.code = src.customer_code
JOIN wms_warehouse warehouse ON warehouse.code = 'WH-SH-01'
GROUP BY src.invoice_no, src.country, src.customer_code, customer.id, warehouse.id, src.order_status, src.day_offset;

INSERT INTO wms_outbound_item
    (order_id, product_id, quantity, allocated_quantity, shipped_quantity, batch_no, created_at, updated_at)
SELECT orders.id, product.id, src.quantity,
       CASE WHEN src.order_status IN ('ALLOCATED', 'PICKED', 'PACKED', 'SHIPPED', 'RETURNED') THEN src.quantity ELSE 0 END,
       CASE WHEN src.order_status IN ('SHIPPED', 'RETURNED') THEN src.quantity ELSE 0 END,
       'UCI-PUBLIC-2010', DATE_SUB(NOW(6), INTERVAL src.day_offset DAY), DATE_SUB(NOW(6), INTERVAL src.day_offset DAY)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src
JOIN wms_outbound_order orders ON orders.order_no = CONCAT('UCI-', src.invoice_no)
JOIN wms_product product ON product.sku = CONCAT('UCI-', src.stock_code)
WHERE NOT EXISTS (
    SELECT 1 FROM wms_outbound_item existing
    WHERE existing.order_id = orders.id AND existing.product_id = product.id
);

INSERT INTO wms_outbound_allocation
    (order_id, order_item_id, inventory_id, quantity, shipped, created_at, updated_at)
SELECT orders.id, item.id, inventory.id, src.quantity,
       src.order_status IN ('SHIPPED', 'RETURNED'),
       DATE_SUB(NOW(6), INTERVAL src.day_offset DAY), DATE_SUB(NOW(6), INTERVAL src.day_offset DAY)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src
JOIN wms_outbound_order orders ON orders.order_no = CONCAT('UCI-', src.invoice_no)
JOIN wms_product product ON product.sku = CONCAT('UCI-', src.stock_code)
JOIN wms_outbound_item item ON item.order_id = orders.id AND item.product_id = product.id
JOIN wms_warehouse warehouse ON warehouse.code = 'WH-SH-01'
JOIN wms_location location ON location.warehouse_id = warehouse.id AND location.code = 'A-01-01'
JOIN wms_inventory_balance inventory ON inventory.warehouse_id = warehouse.id AND inventory.location_id = location.id
    AND inventory.product_id = product.id AND inventory.batch_no = 'UCI-PUBLIC-2010'
WHERE src.order_status IN ('ALLOCATED', 'PICKED', 'PACKED', 'SHIPPED', 'RETURNED')
  AND NOT EXISTS (
      SELECT 1 FROM wms_outbound_allocation existing
      WHERE existing.order_item_id = item.id AND existing.inventory_id = inventory.id
  );

INSERT IGNORE INTO wms_inventory_movement
    (movement_no, type, warehouse_id, location_id, product_id, batch_no, quantity,
     reference_type, reference_id, reason, operator_name, created_at, updated_at)
SELECT CONCAT('UCI-MOV-', src.invoice_no, '-', src.stock_code),
       CASE WHEN src.order_status = 'RETURNED' THEN 'OUTBOUND_RETURN' ELSE 'OUTBOUND_SHIPMENT' END,
       warehouse.id, location.id, product.id, 'UCI-PUBLIC-2010',
       CASE WHEN src.order_status = 'RETURNED' THEN src.quantity ELSE -src.quantity END,
       'OUTBOUND_ORDER', orders.id, 'UCI 真实匿名交易演示流水', 'demo-import',
       DATE_SUB(NOW(6), INTERVAL src.day_offset DAY), DATE_SUB(NOW(6), INTERVAL src.day_offset DAY)
FROM JSON_TABLE(@uci_demo_order_lines, '$[*]' COLUMNS (
    invoice_no VARCHAR(20) PATH '$[0]',
    source_date DATETIME PATH '$[1]',
    country VARCHAR(60) PATH '$[2]',
    customer_code VARCHAR(30) PATH '$[3]',
    customer_name VARCHAR(100) PATH '$[4]',
    stock_code VARCHAR(30) PATH '$[5]',
    description VARCHAR(160) PATH '$[6]',
    quantity BIGINT PATH '$[7]',
    unit_price_gbp DECIMAL(10, 2) PATH '$[8]',
    order_status VARCHAR(30) PATH '$[9]',
    day_offset INT PATH '$[10]'
)) src
JOIN wms_outbound_order orders ON orders.order_no = CONCAT('UCI-', src.invoice_no)
JOIN wms_product product ON product.sku = CONCAT('UCI-', src.stock_code)
JOIN wms_warehouse warehouse ON warehouse.code = 'WH-SH-01'
JOIN wms_location location ON location.warehouse_id = warehouse.id AND location.code = 'A-01-01'
WHERE src.order_status IN ('SHIPPED', 'RETURNED');

SET @uci_demo_order_lines = NULL;
