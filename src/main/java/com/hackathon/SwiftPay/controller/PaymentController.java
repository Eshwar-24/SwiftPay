package com.hackathon.SwiftPay.controller;

import com.hackathon.SwiftPay.dto.ApiResponse;
import com.hackathon.SwiftPay.dto.PaymentRequest;
import com.hackathon.SwiftPay.dto.PaymentResponse;
import com.hackathon.SwiftPay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/payments")
@Tag(name = "Payment", description = "Payment API for P2P transfers")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Initiate a payment",
               description = "Initiates a P2P payment transfer. Uses idempotency key for duplicate prevention.")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {

        log.info("Received payment request: {}", request.getTransactionId());

        PaymentResponse response = paymentService.initiatePayment(request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<PaymentResponse>builder()
                .success(true)
                .message("Payment initiated successfully")
                .data(response)
                .build());
    }

    @GetMapping("/{transactionId}/status")
    @Operation(summary = "Get payment status",
               description = "Retrieves the current status of a payment transaction")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentStatus(
            @PathVariable String transactionId) {

        log.info("Fetching status for transaction: {}", transactionId);

        PaymentResponse response = paymentService.getPaymentStatus(transactionId);

        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
            .success(true)
            .message("Payment status retrieved successfully")
            .data(response)
            .build());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get transaction history",
               description = "Retrieves all transactions (sent and received) for a user")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getTransactionHistory(
            @PathVariable String userId) {

        log.info("Fetching transaction history for user: {}", userId);

        List<PaymentResponse> response = paymentService.getTransactionHistory(userId);

        return ResponseEntity.ok(ApiResponse.<List<PaymentResponse>>builder()
            .success(true)
            .message("Transaction history retrieved successfully")
            .data(response)
            .build());
    }
}

