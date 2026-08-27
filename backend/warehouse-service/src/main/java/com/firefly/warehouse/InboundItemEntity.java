package com.firefly.warehouse;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "wms_inbound_item")
public class InboundItemEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") InboundOrderEntity order;
    @Column(nullable = false) Long productId;
    @Column(nullable = false) Long quantity;
    @Column(nullable = false) Long receivedQuantity = 0L;
    @Column(nullable = false, length = 100) String batchNo = "";
    LocalDate expiryDate;
    protected InboundItemEntity() {}
}
