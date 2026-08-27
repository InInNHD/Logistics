package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_inventory_movement")
public class InventoryMovementEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 60) String movementNo;
    @Column(nullable = false, length = 30) String type;
    @Column(nullable = false) Long warehouseId;
    Long locationId;
    @Column(nullable = false) Long productId;
    @Column(nullable = false, length = 100) String batchNo = "";
    @Column(nullable = false) Long quantity;
    @Column(length = 50) String referenceType;
    Long referenceId;
    @Column(length = 500) String reason;
    @Column(length = 64) String operatorName;
    protected InventoryMovementEntity() {}
}
