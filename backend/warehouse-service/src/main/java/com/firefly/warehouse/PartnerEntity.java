package com.firefly.warehouse;

import jakarta.persistence.*;

@Entity @Table(name = "wms_partner")
public class PartnerEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50) String code;
    @Column(nullable = false, length = 160) String name;
    @Column(nullable = false, length = 30) String type;
    @Column(length = 100) String contact;
    @Column(length = 50) String phone;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    protected PartnerEntity() {}
}
