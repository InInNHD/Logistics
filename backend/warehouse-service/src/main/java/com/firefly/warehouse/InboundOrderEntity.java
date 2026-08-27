package com.firefly.warehouse;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "wms_inbound_order")
public class InboundOrderEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50) String orderNo;
    @Column(nullable = false) Long supplierId;
    @Column(nullable = false) Long warehouseId;
    @Column(nullable = false, length = 30) String status = "PENDING";
    LocalDateTime expectedAt;
    @Column(nullable = false) Long totalQuantity = 0L;
    @Column(nullable = false) Long receivedQuantity = 0L;
    @Column(length = 500) String remark;
    LocalDateTime receivedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC") List<InboundItemEntity> items = new ArrayList<>();
    protected InboundOrderEntity() {}
}
