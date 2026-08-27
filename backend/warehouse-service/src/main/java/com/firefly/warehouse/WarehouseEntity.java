package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_warehouse")
public class WarehouseEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50) String code;
    @Column(nullable = false, length = 100) String name;
    @Column(length = 255) String address;
    @Column(length = 100) String manager;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    protected WarehouseEntity() {}
}
