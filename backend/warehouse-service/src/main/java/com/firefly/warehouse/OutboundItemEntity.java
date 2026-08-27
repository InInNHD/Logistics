package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_outbound_item")
public class OutboundItemEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") OutboundOrderEntity order;
    @Column(nullable = false) Long productId;
    @Column(nullable = false) Long quantity;
    @Column(nullable = false) Long allocatedQuantity = 0L;
    @Column(nullable = false) Long shippedQuantity = 0L;
    @Column(nullable = false, length = 100) String batchNo = "";
    protected OutboundItemEntity() {}
}
