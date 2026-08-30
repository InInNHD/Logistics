package com.firefly.warehouse;

import com.firefly.warehouse.ApiModels.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
class CarrierIntegrationService {
    private static final List<String> REGIONS = List.of("新疆 乌鲁木齐", "新疆 伊犁", "新疆 喀什");
    private static final List<String> STATUSES = List.of("CREATED", "IN_TRANSIT", "SIGNED");
    private final CarrierAccountRepository accounts;
    private final CarrierOrderRepository orders;
    private final CarrierSyncLogRepository logs;
    private final WarehouseRepository warehouses;
    private final CredentialCipher cipher;

    CarrierIntegrationService(CarrierAccountRepository accounts, CarrierOrderRepository orders,
                              CarrierSyncLogRepository logs, WarehouseRepository warehouses, CredentialCipher cipher) {
        this.accounts = accounts; this.orders = orders; this.logs = logs; this.warehouses = warehouses; this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    PageResult<CarrierAccountView> accounts(String keyword, String carrierCode, int page, int size) {
        Page<CarrierAccountEntity> result = accounts.search(query(keyword), upper(carrierCode), pageable(page, size));
        Map<Long, WarehouseEntity> warehouseMap = index(warehouses.findAllById(result.getContent().stream().map(a -> a.warehouseId).toList()));
        return page(result, result.getContent().stream().map(a -> accountView(a, warehouseMap.get(a.warehouseId))).toList());
    }

    @Transactional
    CarrierAccountView create(CarrierAccountRequest request) {
        WarehouseEntity warehouse = warehouses.findById(request.warehouseId()).orElseThrow(() -> WarehouseService.notFound("仓库不存在"));
        if (!"ACTIVE".equalsIgnoreCase(warehouse.status)) throw WarehouseService.conflict("仓库已停用");
        CarrierAccountEntity account = new CarrierAccountEntity();
        account.warehouseId = warehouse.id;
        account.carrierCode = upper(request.carrierCode());
        account.accountName = request.accountName().trim();
        account.apiBaseUrl = request.apiBaseUrl().trim();
        setCredential(account, request.credential());
        account.status = status(request.status());
        account.tokenExpiresAt = request.tokenExpiresAt();
        applySchedule(account, request.syncEnabled(), request.syncIntervalMinutes());
        return accountView(save(account), warehouse);
    }

    @Transactional
    CarrierAccountView update(Long id, CarrierAccountUpdateRequest request) {
        CarrierAccountEntity account = requireAccount(id);
        account.accountName = request.accountName().trim();
        account.apiBaseUrl = request.apiBaseUrl().trim();
        if (request.credential() != null && !request.credential().isBlank()) setCredential(account, request.credential());
        account.status = status(request.status());
        account.tokenExpiresAt = request.tokenExpiresAt();
        account.connectionStatus = "UNTESTED";
        applySchedule(account, request.syncEnabled(), request.syncIntervalMinutes());
        return accountView(save(account), warehouses.findById(account.warehouseId).orElseThrow());
    }

    @Transactional
    CarrierAccountView testConnection(Long id) {
        CarrierAccountEntity account = requireActive(id);
        String credential = cipher.decrypt(account.credentialCiphertext);
        account.connectionStatus = credential.isBlank() ? "FAILED" : "AVAILABLE";
        if (!credential.isBlank()) {
            account.consecutiveFailures = 0;
            account.circuitOpenedUntil = null;
            if (account.syncEnabled && account.nextSyncAt == null) account.nextSyncAt = LocalDateTime.now();
        }
        return accountView(account, warehouses.findById(account.warehouseId).orElseThrow());
    }

    @Transactional
    CarrierSyncResult syncOnce(Long id, String triggerType) {
        CarrierAccountEntity account = requireActive(id);
        if (cipher.decrypt(account.credentialCiphertext).isBlank()) throw WarehouseService.conflict("快递凭证无效");
        LocalDateTime started = LocalDateTime.now();
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 3; i++) {
            String externalNo = account.carrierCode + "-" + account.id + "-" + date + "-" + (i + 1);
            CarrierOrderEntity order = orders.findByAccountIdAndExternalOrderNo(account.id, externalNo).orElseGet(CarrierOrderEntity::new);
            order.accountId = account.id;
            order.externalOrderNo = externalNo;
            order.trackingNo = account.carrierCode + String.format(Locale.ROOT, "%012d", account.id * 100 + i + 1);
            order.recipientRegion = REGIONS.get(i);
            order.status = STATUSES.get(i);
            order.amount = BigDecimal.valueOf(18L + i * 7L);
            order.placedAt = started.minusDays(2L - i);
            order.syncedAt = started;
            orders.save(order);
        }
        account.connectionStatus = "AVAILABLE";
        account.lastSyncedAt = started;
        account.consecutiveFailures = 0;
        account.circuitOpenedUntil = null;
        account.leaseUntil = null;
        account.nextSyncAt = account.syncEnabled ? started.plusMinutes(account.syncIntervalMinutes) : null;
        CarrierSyncLogEntity log = new CarrierSyncLogEntity();
        log.accountId = account.id;
        log.triggerType = triggerType;
        log.status = "SUCCESS";
        log.fetchedCount = 3;
        log.message = "Mock 适配器同步完成，重复订单已更新而非新增";
        log.startedAt = started;
        log.finishedAt = LocalDateTime.now();
        logs.save(log);
        return new CarrierSyncResult(accountView(account, warehouses.findById(account.warehouseId).orElseThrow()), 3);
    }

    @Transactional
    void recordFailure(Long id, String triggerType, int attempts, int threshold, int cooldownMinutes, String failureType) {
        CarrierAccountEntity account = requireAccount(id);
        LocalDateTime now = LocalDateTime.now();
        account.connectionStatus = "FAILED";
        account.consecutiveFailures++;
        account.leaseUntil = null;
        if (account.consecutiveFailures >= threshold) account.circuitOpenedUntil = now.plusMinutes(cooldownMinutes);
        account.nextSyncAt = account.circuitOpenedUntil != null
                ? account.circuitOpenedUntil : now.plusMinutes(account.syncIntervalMinutes);
        CarrierSyncLogEntity log = new CarrierSyncLogEntity();
        log.accountId = id;
        log.triggerType = triggerType;
        log.status = "FAILED";
        log.fetchedCount = 0;
        log.message = "同步失败，已尝试 " + attempts + " 次（" + failureType + "）";
        log.startedAt = now;
        log.finishedAt = now;
        logs.save(log);
    }

    @Transactional(readOnly = true)
    PageResult<CarrierOrderView> orders(String keyword, String status, int page, int size) {
        Page<CarrierOrderEntity> result = orders.search(query(keyword), upper(status), pageable(page, size));
        Map<Long, CarrierAccountEntity> accountMap = index(accounts.findAllById(result.getContent().stream().map(o -> o.accountId).toList()));
        return page(result, result.getContent().stream().map(o -> orderView(o, accountMap.get(o.accountId))).toList());
    }

    @Transactional(readOnly = true)
    PageResult<CarrierSyncLogView> logs(Long accountId, int page, int size) {
        Page<CarrierSyncLogEntity> result = logs.search(accountId, pageable(page, size));
        Map<Long, CarrierAccountEntity> accountMap = index(accounts.findAllById(result.getContent().stream().map(l -> l.accountId).toList()));
        return page(result, result.getContent().stream().map(l -> logView(l, accountMap.get(l.accountId))).toList());
    }

    private CarrierAccountEntity requireAccount(Long id) {
        return accounts.findById(id).orElseThrow(() -> WarehouseService.notFound("快递账号不存在"));
    }

    private CarrierAccountEntity requireActive(Long id) {
        CarrierAccountEntity account = requireAccount(id);
        if (!"ACTIVE".equals(account.status)) throw WarehouseService.conflict("快递账号已停用");
        return account;
    }

    private void setCredential(CarrierAccountEntity account, String credential) {
        String value = credential.trim();
        account.credentialCiphertext = cipher.encrypt(value);
        account.credentialHint = CredentialCipher.hint(value);
    }

    private static void applySchedule(CarrierAccountEntity account, Boolean enabled, Integer intervalMinutes) {
        account.syncEnabled = Boolean.TRUE.equals(enabled);
        account.syncIntervalMinutes = intervalMinutes == null ? 30 : intervalMinutes;
        account.nextSyncAt = account.syncEnabled ? LocalDateTime.now() : null;
        account.leaseUntil = null;
    }

    private CarrierAccountEntity save(CarrierAccountEntity account) {
        try { return accounts.saveAndFlush(account); }
        catch (DataIntegrityViolationException e) { throw WarehouseService.conflict("同一仓库下的快递账号已存在"); }
    }

    private CarrierAccountView accountView(CarrierAccountEntity a, WarehouseEntity w) {
        return new CarrierAccountView(a.id, a.warehouseId, w == null ? "未知仓库" : w.name, a.carrierCode, a.accountName,
                a.apiBaseUrl, a.credentialHint, a.status, a.connectionStatus, a.tokenExpiresAt, a.lastSyncedAt,
                a.syncEnabled, a.syncIntervalMinutes, a.nextSyncAt, a.consecutiveFailures,
                a.circuitOpenedUntil, a.updatedAt);
    }

    private CarrierOrderView orderView(CarrierOrderEntity o, CarrierAccountEntity a) {
        return new CarrierOrderView(o.id, o.accountId, a == null ? "未知账号" : a.accountName,
                a == null ? "UNKNOWN" : a.carrierCode, o.externalOrderNo, o.trackingNo, o.recipientRegion,
                o.status, o.amount, o.placedAt, o.syncedAt);
    }

    private CarrierSyncLogView logView(CarrierSyncLogEntity l, CarrierAccountEntity a) {
        return new CarrierSyncLogView(l.id, l.accountId, a == null ? "未知账号" : a.accountName,
                a == null ? "UNKNOWN" : a.carrierCode, l.triggerType, l.status, l.fetchedCount,
                l.message, l.startedAt, l.finishedAt);
    }

    private static String query(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
    private static String upper(String value) { return query(value).toUpperCase(Locale.ROOT); }
    private static String status(String value) { return value == null || value.isBlank() ? "ACTIVE" : value.toUpperCase(Locale.ROOT); }
    private static PageRequest pageable(int page, int size) { return PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 200)); }
    private static <T> PageResult<T> page(Page<?> page, List<T> values) { return new PageResult<>(values, page.getTotalElements(), page.getNumber() + 1, page.getSize()); }
    private static <T extends BaseEntity> Map<Long, T> index(Collection<T> values) {
        Map<Long, T> result = new HashMap<>();
        for (T value : values) result.put(value.id, value);
        return result;
    }
}
