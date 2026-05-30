package com.hackathon.SwiftPay.dto;

import com.hackathon.SwiftPay.domain.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface LedgerTransactionProjection {

    String getTransactionId();

    String getCounterpartyId();

    String getTransactionType();

    BigDecimal getAmount();

    String getCurrency();

    TransactionStatus getStatus();

    LocalDateTime getCreatedAt();
}
