package com.firefly.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class ApiModels {
    private ApiModels() {}
    record PageResult<T>(List<T> records, long total, int page, int size) {}
    record WarehouseView(Long id, String code, String name, String address, String manager, String status, LocalDateTime createdAt) {}
    record LocationView(Long id, Long warehouseId, String warehouseName, String code, String name, String type, Long capacity, String status) {}
    record ProductView(Long id, String sku, String name, String category, String unit, String barcode, Long safetyStock, String status) {}
    record PartnerView(Long id, String code, String name, String type, String contact, String phone, String status) {}
    record OrderLineView(Long id, Long productId, String sku, String productName, Long quantity, Long allocatedQuantity, Long receivedQuantity, Long shippedQuantity, String batchNo) {}
    record InboundView(Long id, String orderNo, Long supplierId, String supplierName, Long warehouseId, String warehouseName, String status,
                       LocalDateTime expectedAt, Long totalQuantity, Long receivedQuantity, String remark, LocalDateTime createdAt, List<OrderLineView> items) {}
    record InventoryView(Long id, Long warehouseId, String warehouseName, Long locationId, String locationCode, Long productId, String sku,
                         String productName, String batchNo, Long quantity, long availableQuantity, Long allocatedQuantity, Long lockedQuantity,
                         LocalDate expiryDate, LocalDateTime updatedAt) {}
    record InventoryMovementView(Long id, String movementNo, String type, Long warehouseId, String warehouseName,
                                 Long locationId, String locationCode, Long productId, String sku, String productName,
                                 String batchNo, Long quantity, String referenceType, Long referenceId, String reason,
                                 String operatorName, LocalDateTime createdAt) {}
    record OutboundView(Long id, String orderNo, Long customerId, String customerName, Long warehouseId, String warehouseName, String status,
                        LocalDateTime requiredAt, Long totalQuantity, Long allocatedQuantity, Long shippedQuantity, String remark,
                        LocalDateTime createdAt, List<OrderLineView> items) {}
    record ActivityView(Long id, String title, String description, LocalDateTime time, String type) {}
    record DashboardView(long skuCount, long inventoryQuantity, long todayInboundQuantity, long todayOutboundQuantity,
                         long pendingInboundCount, long pendingOutboundCount, long lowStockCount, long expiringCount,
                         List<Long> inboundTrend, List<Long> outboundTrend, List<ActivityView> recentActivities) {}
    record CarrierAccountView(Long id, Long warehouseId, String warehouseName, String carrierCode, String accountName,
                              String apiBaseUrl, String credentialHint, String status, String connectionStatus,
                              LocalDateTime tokenExpiresAt, LocalDateTime lastSyncedAt, Boolean syncEnabled,
                              Integer syncIntervalMinutes, LocalDateTime nextSyncAt, Integer consecutiveFailures,
                              LocalDateTime circuitOpenedUntil, LocalDateTime updatedAt) {}
    record CarrierOrderView(Long id, Long accountId, String accountName, String carrierCode, String externalOrderNo,
                            String trackingNo, String recipientRegion, String status, BigDecimal amount,
                            LocalDateTime placedAt, LocalDateTime syncedAt) {}
    record CarrierSyncLogView(Long id, Long accountId, String accountName, String carrierCode, String triggerType,
                              String status, Integer fetchedCount, String message, LocalDateTime startedAt, LocalDateTime finishedAt) {}
    record CarrierSyncResult(CarrierAccountView account, int fetchedCount) {}
    record CarrierQuoteView(String carrierCode, String destination, BigDecimal weightKg, BigDecimal baseFee,
                            BigDecimal remoteSurcharge, BigDecimal totalFee, int estimatedDays, String serviceLevel) {}
    record CarrierTrackingEventView(String status, String description, String location, LocalDateTime occurredAt) {}
    record CarrierTrackingView(Long orderId, String carrierCode, String trackingNo, String currentStatus,
                               List<CarrierTrackingEventView> events) {}
    record CarrierReconciliationView(String carrierCode, long orderCount, BigDecimal expectedAmount,
                                     BigDecimal billedAmount, BigDecimal differenceAmount, String status) {}

    record WarehouseRequest(@NotBlank String code, @NotBlank String name, String address, String manager,
                            @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status) {}
    record LocationRequest(@NotNull Long warehouseId, @NotBlank String code, @NotBlank String name, String type,
                           @PositiveOrZero Long capacity, @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status) {}
    record ProductRequest(@NotBlank String sku, @NotBlank String name, String category, String unit, String barcode,
                          @PositiveOrZero Long safetyStock, @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status) {}
    record PartnerRequest(@NotBlank String code, @NotBlank String name,
                          @NotBlank @Pattern(regexp = "SUPPLIER|CUSTOMER") String type, String contact, String phone,
                          @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status) {}
    record LineRequest(@NotNull Long productId, @NotNull @Positive Long quantity, String batchNo, LocalDate expiryDate) {}
    record InboundRequest(@NotNull Long supplierId, @NotNull Long warehouseId, LocalDateTime expectedAt, String remark, @NotEmpty List<@Valid LineRequest> items) {}
    record ReceiveLineRequest(@NotNull Long itemId, @NotNull @Positive Long quantity) {}
    record ReceiveRequest(String locationCode, List<@Valid ReceiveLineRequest> items) {}
    record AdjustmentRequest(@NotNull Long warehouseId, @NotBlank String locationCode, @NotNull Long productId, @NotNull @Min(-999999999) @Max(999999999) Long quantity, String batchNo, LocalDate expiryDate, String reason) {}
    record TransferRequest(@NotNull Long inventoryId, String sourceLocationCode, @NotBlank String targetLocationCode, @NotNull @Positive Long quantity, String reason) {}
    record StocktakeRequest(@NotNull Long inventoryId, @NotNull @PositiveOrZero Long actualQuantity, String reason) {}
    record OutboundRequest(@NotNull Long customerId, @NotNull Long warehouseId, LocalDateTime requiredAt, String remark, @NotEmpty List<@Valid LineRequest> items) {}
    record CarrierAccountRequest(@NotNull Long warehouseId,
                                 @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,30}") String carrierCode,
                                 @NotBlank @Size(max = 100) String accountName,
                                 @NotBlank @Pattern(regexp = "(?:https://|mock://).+") @Size(max = 500) String apiBaseUrl,
                                 @NotBlank @Size(max = 500) String credential,
                                 @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status,
                                 LocalDateTime tokenExpiresAt, Boolean syncEnabled,
                                 @Min(1) @Max(1440) Integer syncIntervalMinutes) {}
    record CarrierAccountUpdateRequest(@NotBlank @Size(max = 100) String accountName,
                                       @NotBlank @Pattern(regexp = "(?:https://|mock://).+") @Size(max = 500) String apiBaseUrl,
                                       @Size(max = 500) String credential,
                                       @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status,
                                       LocalDateTime tokenExpiresAt, Boolean syncEnabled,
                                       @Min(1) @Max(1440) Integer syncIntervalMinutes) {}
    record CarrierQuoteRequest(@NotBlank @Pattern(regexp = "(?i)[A-Za-z0-9_-]{2,30}") String carrierCode,
                               @NotBlank @Pattern(regexp = "^新疆.{0,116}", message = "目的地仅支持新疆") String destination,
                               @NotNull @DecimalMin("0.1") @DecimalMax("1000") BigDecimal weightKg) {}
}
