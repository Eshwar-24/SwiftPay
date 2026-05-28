package com.hackathon.SwiftPay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class IdempotencyService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long IDEMPOTENCY_WINDOW_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    public IdempotencyService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicateTransaction(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        Boolean exists = redisTemplate.hasKey(key);
        log.debug("Idempotency check for {}: {}", transactionId, exists != null && exists);
        return exists != null && exists;
    }

    public void markTransactionAsProcessed(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_WINDOW_HOURS, TimeUnit.HOURS);
        log.debug("Transaction marked as processed in Redis: {}", transactionId);
    }

    public void clearIdempotencyKey(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        redisTemplate.delete(key);
        log.debug("Idempotency key cleared: {}", transactionId);
    }
}

