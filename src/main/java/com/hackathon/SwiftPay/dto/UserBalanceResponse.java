package com.hackathon.SwiftPay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBalanceResponse {

    private String userId;
    private BigDecimal balance;
    private String currency;
}

