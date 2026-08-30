package com.firefly.warehouse;

import com.firefly.common.api.ApiResponse;
import com.firefly.warehouse.ApiModels.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
class CarrierIntegrationController {
    private final CarrierIntegrationService service;
    private final CarrierSyncCoordinator coordinator;
    CarrierIntegrationController(CarrierIntegrationService service, CarrierSyncCoordinator coordinator) {
        this.service = service;
        this.coordinator = coordinator;
    }

    @GetMapping("/carrier-accounts")
    ApiResponse<PageResult<CarrierAccountView>> accounts(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String carrierCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.accounts(keyword, carrierCode, page, size));
    }

    @PostMapping("/carrier-accounts")
    ApiResponse<CarrierAccountView> create(@Valid @RequestBody CarrierAccountRequest request) {
        return ApiResponse.ok("快递账号创建成功", service.create(request));
    }

    @PutMapping("/carrier-accounts/{id}")
    ApiResponse<CarrierAccountView> update(@PathVariable Long id, @Valid @RequestBody CarrierAccountUpdateRequest request) {
        return ApiResponse.ok("快递账号更新成功", service.update(id, request));
    }

    @PostMapping("/carrier-accounts/{id}/test")
    ApiResponse<CarrierAccountView> test(@PathVariable Long id) {
        return ApiResponse.ok("Mock 连接测试通过", service.testConnection(id));
    }

    @PostMapping("/carrier-accounts/{id}/sync")
    ApiResponse<CarrierSyncResult> sync(@PathVariable Long id) {
        return ApiResponse.ok("订单同步完成", coordinator.manual(id));
    }

    @GetMapping("/carrier-orders")
    ApiResponse<PageResult<CarrierOrderView>> orders(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.orders(keyword, status, page, size));
    }

    @GetMapping("/carrier-sync-logs")
    ApiResponse<PageResult<CarrierSyncLogView>> logs(
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.logs(accountId, page, size));
    }

    @PostMapping("/carrier-quotes")
    ApiResponse<CarrierQuoteView> quote(@Valid @RequestBody CarrierQuoteRequest request) {
        return ApiResponse.ok(service.quote(request));
    }

    @GetMapping("/carrier-orders/{id}/tracking")
    ApiResponse<CarrierTrackingView> tracking(@PathVariable Long id) {
        return ApiResponse.ok(service.tracking(id));
    }

    @GetMapping("/carrier-reconciliation")
    ApiResponse<java.util.List<CarrierReconciliationView>> reconciliation(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(service.reconciliation(from, to));
    }
}
