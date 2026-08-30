package com.firefly.warehouse;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "wms_carrier_account")
class CarrierAccountEntity extends BaseEntity {
    @Column(nullable = false) Long warehouseId;
    @Column(nullable = false, length = 30) String carrierCode;
    @Column(nullable = false, length = 100) String accountName;
    @Column(nullable = false, length = 500) String apiBaseUrl;
    @Column(nullable = false, length = 1000) String credentialCiphertext;
    @Column(nullable = false, length = 32) String credentialHint;
    @Column(nullable = false, length = 20) String status = "ACTIVE";
    @Column(nullable = false, length = 20) String connectionStatus = "UNTESTED";
    LocalDateTime tokenExpiresAt;
    LocalDateTime lastSyncedAt;
    @Column(nullable = false) Boolean syncEnabled = false;
    @Column(nullable = false) Integer syncIntervalMinutes = 30;
    LocalDateTime nextSyncAt;
    LocalDateTime leaseUntil;
    @Column(nullable = false) Integer consecutiveFailures = 0;
    LocalDateTime circuitOpenedUntil;
    protected CarrierAccountEntity() {}
}

@Entity @Table(name = "wms_carrier_order")
class CarrierOrderEntity extends BaseEntity {
    @Column(nullable = false) Long accountId;
    @Column(nullable = false, length = 80) String externalOrderNo;
    @Column(length = 80) String trackingNo;
    @Column(length = 120) String recipientRegion;
    @Column(nullable = false, length = 30) String status;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal amount = BigDecimal.ZERO;
    LocalDateTime placedAt;
    @Column(nullable = false) LocalDateTime syncedAt;
    protected CarrierOrderEntity() {}
}

@Entity @Table(name = "wms_carrier_sync_log")
class CarrierSyncLogEntity extends BaseEntity {
    @Column(nullable = false) Long accountId;
    @Column(nullable = false, length = 20) String triggerType;
    @Column(nullable = false, length = 20) String status;
    @Column(nullable = false) Integer fetchedCount = 0;
    @Column(length = 500) String message;
    @Column(nullable = false) LocalDateTime startedAt;
    @Column(nullable = false) LocalDateTime finishedAt;
    protected CarrierSyncLogEntity() {}
}
