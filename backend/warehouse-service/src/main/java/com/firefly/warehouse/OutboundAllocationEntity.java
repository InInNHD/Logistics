package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_outbound_allocation")
public class OutboundAllocationEntity extends BaseEntity {
    @Column(nullable = false) Long orderId;
    @Column(nullable = false) Long orderItemId;
    @Column(nullable = false) Long inventoryId;
    @Column(nullable = false) Long quantity;
    @Column(nullable = false) boolean shipped = false;
    protected OutboundAllocationEntity() {}
}
