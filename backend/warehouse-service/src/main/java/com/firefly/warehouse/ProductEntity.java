package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_product")
public class ProductEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 80) String sku;
    @Column(nullable = false, length = 160) String name;
    @Column(length = 100) String category;
    @Column(nullable = false, length = 20) String unit = "件";
    @Column(unique = true, length = 100) String barcode;
    @Column(nullable = false) Long safetyStock = 0L;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    protected ProductEntity() {}
}
