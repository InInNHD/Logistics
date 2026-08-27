package com.firefly.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "wms_idempotency_record", uniqueConstraints = @UniqueConstraint(
        name = "uk_idempotency_operation_key", columnNames = {"operation", "idempotency_key"}))
class IdempotencyRecordEntity extends BaseEntity {
    @Column(nullable = false, length = 64)
    String operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    String idempotencyKey;

    @Column(nullable = false, length = 64)
    String requestHash;

    @Column(nullable = false, length = 20)
    String status = "PROCESSING";

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    String responseBody;

    protected IdempotencyRecordEntity() {}
}
