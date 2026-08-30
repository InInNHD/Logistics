package com.firefly.warehouse;

import com.firefly.warehouse.ApiModels.CarrierSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
class CarrierSyncCoordinator {
    private final CarrierAccountRepository accounts;
    private final CarrierIntegrationService service;
    private final ApplicationEventPublisher events;
    private final int batchSize;
    private final int leaseMinutes;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final long manualCooldownSeconds;
    private final int circuitThreshold;
    private final int circuitCooldownMinutes;

    CarrierSyncCoordinator(CarrierAccountRepository accounts, CarrierIntegrationService service,
            ApplicationEventPublisher events,
            @Value("${firefly.carrier.batch-size:50}") int batchSize,
            @Value("${firefly.carrier.lease-minutes:5}") int leaseMinutes,
            @Value("${firefly.carrier.max-attempts:3}") int maxAttempts,
            @Value("${firefly.carrier.retry-delay-ms:500}") long retryDelayMs,
            @Value("${firefly.carrier.manual-min-interval-seconds:5}") long manualCooldownSeconds,
            @Value("${firefly.carrier.circuit-failure-threshold:3}") int circuitThreshold,
            @Value("${firefly.carrier.circuit-cooldown-minutes:15}") int circuitCooldownMinutes) {
        this.accounts = accounts;
        this.service = service;
        this.events = events;
        this.batchSize = Math.max(1, Math.min(batchSize, 200));
        this.leaseMinutes = Math.max(1, leaseMinutes);
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        this.retryDelayMs = Math.max(0, Math.min(retryDelayMs, 5000));
        this.manualCooldownSeconds = Math.max(0, manualCooldownSeconds);
        this.circuitThreshold = Math.max(1, circuitThreshold);
        this.circuitCooldownMinutes = Math.max(1, circuitCooldownMinutes);
    }

    CarrierSyncResult manual(Long id) {
        LocalDateTime now = LocalDateTime.now();
        if (accounts.claimManual(id, now, now.minusSeconds(manualCooldownSeconds), now.plusMinutes(leaseMinutes)) == 0) {
            CarrierAccountEntity account = accounts.findById(id)
                    .orElseThrow(() -> WarehouseService.notFound("快递账号不存在"));
            if (!"ACTIVE".equals(account.status)) throw WarehouseService.conflict("快递账号已停用");
            if (account.circuitOpenedUntil != null && account.circuitOpenedUntil.isAfter(now)) {
                throw new BusinessException(429, "快递接口处于熔断保护中，请稍后重试");
            }
            if (account.lastSyncedAt != null && account.lastSyncedAt.isAfter(now.minusSeconds(manualCooldownSeconds))) {
                throw new BusinessException(429, "同步过于频繁，请稍后重试");
            }
            throw WarehouseService.conflict("该快递账号正在同步");
        }
        return runClaimed(id, "MANUAL");
    }

    void syncDueAccounts() {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : accounts.findDueIds(now, PageRequest.of(0, batchSize))) {
            if (accounts.claimDue(id, now, now.plusMinutes(leaseMinutes)) == 0) continue;
            try { runClaimed(id, "SCHEDULED"); }
            catch (RuntimeException ignored) { /* 失败状态已持久化，继续隔离处理其他账号。 */ }
        }
    }

    private CarrierSyncResult runClaimed(Long id, String triggerType) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                CarrierSyncResult result = service.syncOnce(id, triggerType);
                events.publishEvent(new CarrierSyncEvent(id, triggerType, "SUCCESS", result.fetchedCount()));
                return result;
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt < maxAttempts) {
                    try { pause(); }
                    catch (RuntimeException interrupted) { last = interrupted; break; }
                }
            }
        }
        String failureType = last == null ? "UnknownFailure" : last.getClass().getSimpleName();
        service.recordFailure(id, triggerType, maxAttempts, circuitThreshold, circuitCooldownMinutes, failureType);
        events.publishEvent(new CarrierSyncEvent(id, triggerType, "FAILED", 0));
        throw last == null ? new IllegalStateException("Carrier synchronization failed") : last;
    }

    private void pause() {
        if (retryDelayMs == 0) return;
        try { Thread.sleep(retryDelayMs); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Carrier synchronization interrupted", exception);
        }
    }
}

@Component
@ConditionalOnProperty(name = "firefly.carrier.scheduler-enabled", havingValue = "true")
class CarrierSyncScheduler {
    private final CarrierSyncCoordinator coordinator;
    CarrierSyncScheduler(CarrierSyncCoordinator coordinator) { this.coordinator = coordinator; }

    @Scheduled(fixedDelayString = "${firefly.carrier.scheduler-delay-ms:60000}", initialDelayString = "${firefly.carrier.scheduler-delay-ms:60000}")
    void synchronize() { coordinator.syncDueAccounts(); }
}

record CarrierSyncEvent(Long accountId, String triggerType, String status, int fetchedCount) {}

@Component
class CarrierSyncEventListener {
    private static final Logger log = LoggerFactory.getLogger(CarrierSyncEventListener.class);

    @Async
    @EventListener
    void onSync(CarrierSyncEvent event) {
        log.info("Carrier sync event accountId={}, trigger={}, status={}, fetchedCount={}",
                event.accountId(), event.triggerType(), event.status(), event.fetchedCount());
    }
}
