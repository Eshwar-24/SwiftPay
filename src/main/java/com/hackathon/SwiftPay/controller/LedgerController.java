package com.hackathon.SwiftPay.controller;

import com.hackathon.SwiftPay.dto.ApiResponse;
import com.hackathon.SwiftPay.dto.TransactionHistoryResponse;
import com.hackathon.SwiftPay.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ledger")
@Tag(name = "Ledger", description = "Ledger API for account history and reporting")
@Slf4j
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get transaction history with balances",
               description = "Retrieves detailed transaction history including balance information")
    public ResponseEntity<ApiResponse<TransactionHistoryResponse>> getTransactionHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching detailed transaction history for user: {}, page: {}, size: {}", userId, page, size);

        TransactionHistoryResponse response = ledgerService.getTransactionHistory(userId, page, size);

        return ResponseEntity.ok(ApiResponse.<TransactionHistoryResponse>builder()
            .success(true)
            .message("Transaction history retrieved successfully")
            .data(response)
            .build());
    }
}

