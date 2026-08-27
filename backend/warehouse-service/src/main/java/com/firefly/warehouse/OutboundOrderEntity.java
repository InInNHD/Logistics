package com.firefly.warehouse;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "wms_outbound_order")
public class OutboundOrderEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50) String orderNo;
    @Column(nullable = false) Long customerId;
    @Column(nullable = false) Long warehouseId;
    @Column(nullable = false, length = 30) String status = "PENDING";
    LocalDateTime requiredAt;
    @Column(nullable = false) Long totalQuantity = 0L;
    @Column(nullable = false) Long allocatedQuantity = 0L;
    @Column(nullable = false) Long shippedQuantity = 0L;
    @Column(length = 500) String remark;
    LocalDateTime shippedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC") List<OutboundItemEntity> items = new ArrayList<>();
    protected OutboundOrderEntity() {}
}
