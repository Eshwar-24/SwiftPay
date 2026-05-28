package com.hackathon.SwiftPay.service;

import com.hackathon.SwiftPay.domain.entity.Payment;
import com.hackathon.SwiftPay.domain.entity.UserBalance;
import com.hackathon.SwiftPay.domain.enums.TransactionStatus;
import com.hackathon.SwiftPay.dto.PaymentRequest;
import com.hackathon.SwiftPay.dto.PaymentResponse;
import com.hackathon.SwiftPay.exception.InsufficientFundsException;
import com.hackathon.SwiftPay.exception.IdempotencyException;
import com.hackathon.SwiftPay.repository.PaymentRepository;
import com.hackathon.SwiftPay.repository.UserBalanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final IdempotencyService idempotencyService;
    private final KafkaProducerService kafkaProducerService;
    private final BalanceService balanceService;

    public PaymentService(PaymentRepository paymentRepository,
                         UserBalanceRepository userBalanceRepository,
                         IdempotencyService idempotencyService,
                         KafkaProducerService kafkaProducerService,
                         BalanceService balanceService) {
        this.paymentRepository = paymentRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.idempotencyService = idempotencyService;
        this.kafkaProducerService = kafkaProducerService;
        this.balanceService = balanceService;
    }

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        log.info("Initiating payment: {} from {} to {}",
            request.getTransactionId(), request.getSenderId(), request.getReceiverId());

        // Check idempotency
        if (idempotencyService.isDuplicateTransaction(request.getTransactionId())) {
            log.warn("Duplicate transaction detected: {}", request.getTransactionId());
//            Optional<Payment> existing = paymentRepository.findByTransactionId(request.getTransactionId());
//            if (existing.isPresent()) {
//                return convertToResponse(existing.get());
//            }
            throw new IdempotencyException("Transaction already processed within 24 hours");
        }

        // Validate sender has sufficient balance
        balanceService.validateBalance(request.getSenderId(), request.getAmount());

        // Create payment record with PENDING status
        Payment payment = Payment.builder()
            .transactionId(request.getTransactionId())
            .senderId(request.getSenderId())
            .receiverId(request.getReceiverId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .status(TransactionStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment saved with PENDING status: {}", savedPayment.getId());

        // Mark as processed in Redis
        idempotencyService.markTransactionAsProcessed(request.getTransactionId());

        // Emit PaymentInitiated event to Kafka
        kafkaProducerService.sendPaymentInitiated(savedPayment);
        log.info("PaymentInitiated event sent to Kafka for: {}", request.getTransactionId());

        return convertToResponse(savedPayment);
    }

    public List<PaymentResponse> getTransactionHistory(String userId) {
        log.info("Fetching transaction history for user: {}", userId);
        List<Payment> payments = paymentRepository.findTransactionHistoryByUserId(userId);
        return payments.stream()
            .map(this::convertToResponse)
            .toList();
    }

    public PaymentResponse getPaymentStatus(String transactionId) {
        log.info("Fetching status for transaction: {}", transactionId);
        Payment payment = paymentRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + transactionId));
        return convertToResponse(payment);
    }

    @Transactional
    public void updatePaymentStatus(String transactionId, TransactionStatus status, String failureReason) {
        log.info("Updating payment status for {}: {}", transactionId, status);
        Payment payment = paymentRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + transactionId));

        payment.setStatus(status);
        payment.setFailureReason(failureReason);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private PaymentResponse convertToResponse(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .transactionId(payment.getTransactionId())
            .senderId(payment.getSenderId())
            .receiverId(payment.getReceiverId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .failureReason(payment.getFailureReason())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }
}

