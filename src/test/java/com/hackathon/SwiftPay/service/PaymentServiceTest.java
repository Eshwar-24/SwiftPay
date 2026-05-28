package com.hackathon.SwiftPay.service;

import com.hackathon.SwiftPay.domain.entity.Payment;
import com.hackathon.SwiftPay.domain.entity.UserBalance;
import com.hackathon.SwiftPay.domain.enums.TransactionStatus;
import com.hackathon.SwiftPay.dto.PaymentRequest;
import com.hackathon.SwiftPay.exception.InsufficientFundsException;
import com.hackathon.SwiftPay.exception.IdempotencyException;
import com.hackathon.SwiftPay.repository.PaymentRepository;
import com.hackathon.SwiftPay.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserBalanceRepository userBalanceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private IdempotencyService idempotencyService;

    @BeforeEach
    public void setup() {
        // Clear Redis and repositories
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        paymentRepository.deleteAll();
        userBalanceRepository.deleteAll();

        // Create test users with balances
        UserBalance user1 = UserBalance.builder()
            .userId("user1")
            .balance(new BigDecimal("10000.00"))
            .currency("USD")
            .build();
        userBalanceRepository.save(user1);

        UserBalance user2 = UserBalance.builder()
            .userId("user2")
            .balance(new BigDecimal("5000.00"))
            .currency("USD")
            .build();
        userBalanceRepository.save(user2);
    }

    @Test
    public void testSuccessfulPaymentInitiation() {
        PaymentRequest request = PaymentRequest.builder()
            .transactionId("TXN-UNIT-001")
            .senderId("user1")
            .receiverId("user2")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();

        var response = paymentService.initiatePayment(request);

        assertNotNull(response);
        assertEquals("TXN-UNIT-001", response.getTransactionId());
        assertEquals(TransactionStatus.PENDING, response.getStatus());

        // Verify in database
        Optional<Payment> savedPayment = paymentRepository.findByTransactionId("TXN-UNIT-001");
        assertTrue(savedPayment.isPresent());
    }

    @Test
    public void testDuplicatePaymentDetection() {
        PaymentRequest request = PaymentRequest.builder()
            .transactionId("TXN-DUP-001")
            .senderId("user1")
            .receiverId("user2")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();

        // First payment should succeed
        paymentService.initiatePayment(request);

        // Second payment with same transaction ID should throw IdempotencyException
        assertThrows(IdempotencyException.class, () -> {
            paymentService.initiatePayment(request);
        });
    }

    @Test
    public void testTransactionHistoryRetrieval() {
        PaymentRequest request = PaymentRequest.builder()
            .transactionId("TXN-HIST-001")
            .senderId("user1")
            .receiverId("user2")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();

        paymentService.initiatePayment(request);

        var history = paymentService.getTransactionHistory("user1");

        assertNotNull(history);
        assertTrue(history.size() > 0);
    }
}

