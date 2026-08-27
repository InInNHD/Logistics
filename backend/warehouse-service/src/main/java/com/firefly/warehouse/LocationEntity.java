package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_location", uniqueConstraints = @UniqueConstraint(name = "uk_location_warehouse_code", columnNames = {"warehouse_id", "code"}))
public class LocationEntity extends BaseEntity {
    @Column(name = "warehouse_id", nullable = false) Long warehouseId;
    @Column(nullable = false, length = 50) String code;
    @Column(nullable = false, length = 100) String name;
    @Column(nullable = false, length = 30) String type = "STORAGE";
    @Column(nullable = false) Long capacity = 0L;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    protected LocationEntity() {}
}
