package com.firefly.warehouse;

import com.firefly.common.api.ApiResponse;
import com.firefly.warehouse.ApiModels.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class WarehouseController {
    private final WarehouseService service;
    private final IdempotencyCoordinator idempotency;

    WarehouseController(WarehouseService service, IdempotencyCoordinator idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping("/dashboard/summary")
    ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }

    @GetMapping("/warehouses")
    ApiResponse<PageResult<WarehouseView>> warehouses(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.warehouses(keyword, page, size));
    }

    @PostMapping("/warehouses")
    ApiResponse<WarehouseView> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.ok("仓库创建成功", service.createWarehouse(request));
    }

    @GetMapping("/locations")
    ApiResponse<PageResult<LocationView>> locations(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(service.locations(keyword, warehouseId, page, size));
    }

    @PostMapping("/locations")
    ApiResponse<LocationView> createLocation(@Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok("库位创建成功", service.createLocation(request));
    }

    @GetMapping("/products")
    ApiResponse<PageResult<ProductView>> products(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(service.products(keyword, page, size));
    }

    @PostMapping("/products")
    ApiResponse<ProductView> createProduct(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("商品创建成功", service.createProduct(request));
    }

    @GetMapping("/partners")
    ApiResponse<PageResult<PartnerView>> partners(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(service.partners(keyword, type, page, size));
    }

    @PostMapping("/partners")
    ApiResponse<PartnerView> createPartner(@Valid @RequestBody PartnerRequest request) {
        return ApiResponse.ok("往来单位创建成功", service.createPartner(request));
    }

    @GetMapping("/inbound-orders")
    ApiResponse<PageResult<InboundView>> inboundOrders(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.inboundOrders(keyword, status, page, size));
    }

    @PostMapping("/inbound-orders")
    ApiResponse<InboundView> createInbound(
            @Valid @RequestBody InboundRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        InboundView result = idempotency.execute(idempotencyKey, "CREATE_INBOUND", request,
                InboundView.class, () -> service.createInbound(request));
        return ApiResponse.ok("入库单创建成功", result);
    }

    @PostMapping("/inbound-orders/{id}/receive")
    ApiResponse<InboundView> receive(
            @PathVariable(name = "id") Long id,
            @RequestBody(required = false) ReceiveRequest request,
            @RequestHeader(name = "X-Username", defaultValue = "system") String operator,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        InboundView result = idempotency.execute(idempotencyKey, "RECEIVE_INBOUND",
                new Object[]{id, request}, InboundView.class, () -> service.receive(id, request, operator));
        return ApiResponse.ok("收货完成，库存已入账", result);
    }

    @GetMapping("/inventory")
    ApiResponse<PageResult<InventoryView>> inventory(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(service.inventory(keyword, warehouseId, page, size));
    }

    @GetMapping("/inventory/movements")
    ApiResponse<PageResult<InventoryMovementView>> movements(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ApiResponse.ok(service.movements(keyword, warehouseId, type, page, size));
    }

    @PostMapping("/inventory/adjustments")
    ApiResponse<InventoryView> adjust(
            @Valid @RequestBody AdjustmentRequest request,
            @RequestHeader(name = "X-Username", defaultValue = "system") String operator,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        InventoryView result = idempotency.execute(idempotencyKey, "ADJUST_INVENTORY", request,
                InventoryView.class, () -> service.adjust(request, operator));
        return ApiResponse.ok("库存调整成功", result);
    }

    @PostMapping("/inventory/transfers")
    ApiResponse<InventoryView> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(name = "X-Username", defaultValue = "system") String operator,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        InventoryView result = idempotency.execute(idempotencyKey, "TRANSFER_INVENTORY", request,
                InventoryView.class, () -> service.transfer(request, operator));
        return ApiResponse.ok("库存移库成功", result);
    }

    @GetMapping("/outbound-orders")
    ApiResponse<PageResult<OutboundView>> outboundOrders(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.outboundOrders(keyword, status, page, size));
    }

    @PostMapping("/outbound-orders")
    ApiResponse<OutboundView> createOutbound(
            @Valid @RequestBody OutboundRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        OutboundView result = idempotency.execute(idempotencyKey, "CREATE_OUTBOUND", request,
                OutboundView.class, () -> service.createOutbound(request));
        return ApiResponse.ok("出库单创建成功", result);
    }

    @PostMapping("/outbound-orders/{id}/allocate")
    ApiResponse<OutboundView> allocate(
            @PathVariable(name = "id") Long id,
            @RequestHeader(name = "X-Username", defaultValue = "system") String operator,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        OutboundView result = idempotency.execute(idempotencyKey, "ALLOCATE_OUTBOUND", id,
                OutboundView.class, () -> service.allocate(id, operator));
        return ApiResponse.ok("库存分配成功", result);
    }

    @PostMapping("/outbound-orders/{id}/ship")
    ApiResponse<OutboundView> ship(
            @PathVariable(name = "id") Long id,
            @RequestHeader(name = "X-Username", defaultValue = "system") String operator,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        OutboundView result = idempotency.execute(idempotencyKey, "SHIP_OUTBOUND", id,
                OutboundView.class, () -> service.ship(id, operator));
        return ApiResponse.ok("发运成功，库存已扣减", result);
    }
}
