package com.hackathon.SwiftPay.service;

import com.hackathon.SwiftPay.domain.entity.Ledger;
import com.hackathon.SwiftPay.domain.entity.Payment;
import com.hackathon.SwiftPay.domain.enums.TransactionStatus;
import com.hackathon.SwiftPay.dto.LedgerTransactionProjection;
import com.hackathon.SwiftPay.dto.PaymentEvent;
import com.hackathon.SwiftPay.dto.TransactionHistoryResponse;
import com.hackathon.SwiftPay.exception.PaymentException;
import com.hackathon.SwiftPay.repository.LedgerRepository;
import com.hackathon.SwiftPay.repository.PaymentRepository;
import com.hackathon.SwiftPay.repository.UserBalanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class LedgerService {

    private final LedgerRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final BalanceService balanceService;
    private final KafkaProducerService kafkaProducerService;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public LedgerService(LedgerRepository ledgerRepository,
                        PaymentRepository paymentRepository,
                        UserBalanceRepository userBalanceRepository,
                        BalanceService balanceService,
                        KafkaProducerService kafkaProducerService) {
        this.ledgerRepository = ledgerRepository;
        this.paymentRepository = paymentRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.balanceService = balanceService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @KafkaListener(topics = "${kafka.topic.payment-initiated:payment-initiated}",
                   groupId = "${spring.application.name:SwiftPay}")
    @Transactional
    public void consumePaymentInitiated(PaymentEvent event) {
        log.info("Consuming PaymentInitiated event for transaction: {}", event.getTransactionId());

        try {
            // Fetch payment from DB
            Payment payment = paymentRepository.findByTransactionId(event.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getTransactionId()));

            // Validate sender has sufficient balance
            balanceService.validateBalance(event.getSenderId(), event.getAmount());

            // Perform balance transfer (debit sender, credit receiver)
            balanceService.debitBalance(event.getSenderId(), event.getAmount());
            balanceService.creditBalance(event.getReceiverId(), event.getAmount());

            // Record ledger entries
            recordLedgerEntry(payment.getId(), event.getSenderId(), event.getTransactionId(),
                "DEBIT", event.getAmount(), event.getCurrency(), "Payment sent");

            recordLedgerEntry(payment.getId(), event.getReceiverId(), event.getTransactionId(),
                "CREDIT", event.getAmount(), event.getCurrency(), "Payment received");

            // Update payment status to COMPLETED
            payment.setStatus(TransactionStatus.COMPLETED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Send PaymentCompleted event
            kafkaProducerService.sendPaymentCompleted(payment);
            log.info("Payment completed successfully: {}", event.getTransactionId());

        } catch (Exception e) {
            log.error("Error processing payment: {}", event.getTransactionId(), e);

            // Update payment status to FAILED
            Payment payment = paymentRepository.findByTransactionId(event.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getTransactionId()));

            payment.setStatus(TransactionStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Send PaymentFailed event
            kafkaProducerService.sendPaymentFailed(payment, e.getMessage());

            // Rethrow to trigger retry mechanism
            throw new PaymentException("Payment processing failed: " + e.getMessage());
        }
    }

    @Transactional
    public void recordLedgerEntry(Long paymentId, String userId, String transactionId,
                                  String type, BigDecimal amount, String currency, String description) {
        BigDecimal balanceAfter = balanceService.getBalance(userId);

        Ledger ledger = Ledger.builder()
            .userId(userId)
            .paymentId(paymentId)
            .transactionId(transactionId)
            .type(type)
            .amount(amount)
            .currency(currency)
            .balanceAfter(balanceAfter)
            .description(description)
            .createdAt(LocalDateTime.now())
            .build();

        ledgerRepository.save(ledger);
        log.debug("Ledger entry recorded for user {}: {} {}", userId, type, amount);
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(String userId) {
        return getTransactionHistory(userId, 0, DEFAULT_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        log.info("Fetching transaction history for user: {}, page: {}, size: {}", userId, safePage, safeSize);

        // Get current balance
        BigDecimal currentBalance = balanceService.getBalance(userId);

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Slice<LedgerTransactionProjection> transactionSlice =
            ledgerRepository.findUserTransactionHistory(userId, pageable);

        List<TransactionHistoryResponse.Transaction> transactions = transactionSlice.getContent().stream()
            .map(entry -> TransactionHistoryResponse.Transaction.builder()
                .transactionId(entry.getTransactionId())
                .counterpartyId(entry.getCounterpartyId() != null ? entry.getCounterpartyId() : "Unknown")
                .type(entry.getTransactionType())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .status(entry.getStatus() != null ? entry.getStatus() : TransactionStatus.PENDING)
                .createdAt(entry.getCreatedAt())
                .build())
            .toList();

        return TransactionHistoryResponse.builder()
            .userId(userId)
            .currentBalance(currentBalance)
            .currency("USD")
            .page(transactionSlice.getNumber())
            .size(transactionSlice.getSize())
            .hasNext(transactionSlice.hasNext())
            .hasPrevious(transactionSlice.hasPrevious())
            .transactions(transactions)
            .build();
    }
}

