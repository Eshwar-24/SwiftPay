package com.hackathon.SwiftPay.service;

import com.hackathon.SwiftPay.domain.entity.UserBalance;
import com.hackathon.SwiftPay.exception.InsufficientFundsException;
import com.hackathon.SwiftPay.exception.UserNotFoundException;
import com.hackathon.SwiftPay.repository.UserBalanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BalanceService {

    private static final String BALANCE_CACHE_KEY_PREFIX = "balance:";
    private static final long BALANCE_CACHE_TTL_MINUTES = 5;

    private final UserBalanceRepository userBalanceRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public BalanceService(UserBalanceRepository userBalanceRepository,
                         RedisTemplate<String, Object> redisTemplate) {
        this.userBalanceRepository = userBalanceRepository;
        this.redisTemplate = redisTemplate;
    }

    public BigDecimal getBalance(String userId) {
        log.debug("Fetching balance for user: {}", userId);

        // Try cache first
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        Object cachedBalance = redisTemplate.opsForValue().get(cacheKey);
        if (cachedBalance != null) {
            log.debug("Balance found in cache for user: {}", userId);
            return new BigDecimal(cachedBalance.toString());
        }

        // Fetch from database
        UserBalance userBalance = userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Cache the balance
        redisTemplate.opsForValue().set(cacheKey, userBalance.getBalance().toString(),
            BALANCE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Balance cached for user: {}", userId);

        return userBalance.getBalance();
    }

    public void validateBalance(String userId, BigDecimal requiredAmount) {
        BigDecimal balance = getBalance(userId);
        if (balance.compareTo(requiredAmount) < 0) {
            log.warn("Insufficient funds for user {}: required={}, available={}",
                userId, requiredAmount, balance);
            throw new InsufficientFundsException(
                String.format("Insufficient balance. Required: %s, Available: %s",
                    requiredAmount, balance));
        }
        log.debug("Balance validation passed for user: {}", userId);
    }

    @Transactional
    public void debitBalance(String userId, BigDecimal amount) {
        log.info("Debiting balance for user {}: {}", userId, amount);
        UserBalance userBalance = userBalanceRepository.findByUserId(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        BigDecimal newBalance = userBalance.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException("Transaction would result in negative balance");
        }

        userBalance.setBalance(newBalance);
        userBalanceRepository.save(userBalance);

        // Invalidate cache
        invalidateBalanceCache(userId);
        log.info("Balance debited for user {}: new balance = {}", userId, newBalance);
    }

    @Transactional
    public void creditBalance(String userId, BigDecimal amount) {
        log.info("Crediting balance for user {}: {}", userId, amount);
        UserBalance userBalance = userBalanceRepository.findByUserId(userId)
            .orElseGet(() -> createNewUserBalance(userId));

        BigDecimal newBalance = userBalance.getBalance().add(amount);
        userBalance.setBalance(newBalance);
        userBalanceRepository.save(userBalance);

        // Invalidate cache
        invalidateBalanceCache(userId);
        log.info("Balance credited for user {}: new balance = {}", userId, newBalance);
    }

    private UserBalance createNewUserBalance(String userId) {
        log.info("Creating new balance record for user: {}", userId);
        UserBalance userBalance = UserBalance.builder()
            .userId(userId)
            .balance(BigDecimal.ZERO)
            .currency("USD")
            .build();
        return userBalanceRepository.save(userBalance);
    }

    public void invalidateBalanceCache(String userId) {
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        log.debug("Balance cache invalidated for user: {}", userId);
    }
}

