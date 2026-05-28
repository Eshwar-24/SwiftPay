package com.hackathon.SwiftPay.dto;

import com.hackathon.SwiftPay.domain.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistoryResponse {

    private String userId;
    private BigDecimal currentBalance;
    private String currency;
    private List<Transaction> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Transaction {
        private String transactionId;
        private String counterpartyId;
        private String type; // SENT or RECEIVED
        private BigDecimal amount;
        private String currency;
        private TransactionStatus status;
        private LocalDateTime createdAt;
    }
}

