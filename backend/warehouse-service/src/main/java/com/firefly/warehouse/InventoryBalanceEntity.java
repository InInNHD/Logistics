package com.firefly.warehouse;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "wms_inventory_balance", uniqueConstraints = @UniqueConstraint(name = "uk_inventory_dimension", columnNames = {"warehouse_id", "location_id", "product_id", "batch_no"}))
public class InventoryBalanceEntity extends BaseEntity {
    @Version Long version;
    @Column(nullable = false) Long warehouseId;
    @Column(nullable = false) Long locationId;
    @Column(nullable = false) Long productId;
    @Column(nullable = false, length = 100) String batchNo = "";
    LocalDate expiryDate;
    @Column(nullable = false) Long quantity = 0L;
    @Column(nullable = false) Long allocatedQuantity = 0L;
    @Column(nullable = false) Long lockedQuantity = 0L;
    protected InventoryBalanceEntity() {}
    long available() { return quantity - allocatedQuantity - lockedQuantity; }
}
