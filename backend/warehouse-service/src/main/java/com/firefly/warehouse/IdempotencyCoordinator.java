package com.firefly.warehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Component
class IdempotencyCoordinator {
    private final IdempotencyRecordRepository records;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    IdempotencyCoordinator(IdempotencyRecordRepository records, ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.records = records;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    <T> T execute(String key, String operation, Object requestFingerprint, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) return action.get();
        String normalizedKey = key.trim();
        if (normalizedKey.length() > 128) throw WarehouseService.bad("Idempotency-Key 长度不能超过 128 个字符");
        String requestHash = hash(operation, requestFingerprint);
        try {
            return transactions.execute(status -> executeInTransaction(
                    normalizedKey, operation, requestHash, responseType, action));
        } catch (DataIntegrityViolationException race) {
            // 两个相同请求可能同时看不到记录；唯一约束会使后提交者回滚，再读取先提交者的结果。
            T replay = transactions.execute(status -> records.findByOperationAndIdempotencyKey(operation, normalizedKey)
                    .map(record -> replay(record, requestHash, responseType))
                    .orElse(null));
            if (replay != null) return replay;
            throw race;
        }
    }

    private <T> T executeInTransaction(String key, String operation, String requestHash,
                                       Class<T> responseType, Supplier<T> action) {
        var existing = records.findByOperationAndIdempotencyKey(operation, key);
        if (existing.isPresent()) return replay(existing.get(), requestHash, responseType);

        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.operation = operation;
        record.idempotencyKey = key;
        record.requestHash = requestHash;
        records.saveAndFlush(record);

        T result = action.get();
        record.responseBody = write(result);
        record.status = "COMPLETED";
        records.save(record);
        return result;
    }

    private <T> T replay(IdempotencyRecordEntity record, String requestHash, Class<T> responseType) {
        if (!record.requestHash.equals(requestHash)) {
            throw WarehouseService.conflict("同一 Idempotency-Key 不能用于不同请求");
        }
        if (!"COMPLETED".equals(record.status) || record.responseBody == null) {
            throw WarehouseService.conflict("相同请求正在处理中，请稍后重试");
        }
        try {
            return objectMapper.readValue(record.responseBody, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取幂等请求结果", e);
        }
    }

    private String hash(String operation, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("无法生成幂等请求摘要", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存幂等请求结果", e);
        }
    }
}
