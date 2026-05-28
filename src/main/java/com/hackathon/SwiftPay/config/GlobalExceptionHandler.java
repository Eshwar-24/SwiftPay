package com.hackathon.SwiftPay.config;

import com.hackathon.SwiftPay.dto.ApiResponse;
import com.hackathon.SwiftPay.exception.IdempotencyException;
import com.hackathon.SwiftPay.exception.InsufficientFundsException;
import com.hackathon.SwiftPay.exception.PaymentException;
import com.hackathon.SwiftPay.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentException(
            PaymentException ex, WebRequest request) {
        log.error("Payment exception occurred: {} - {}", ex.getCode(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyException(
            IdempotencyException ex, WebRequest request) {
        log.error("Idempotency violation: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Duplicate transaction")
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientFundsException(
            InsufficientFundsException ex, WebRequest request) {
        log.error("Insufficient funds: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Insufficient balance")
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {
        log.error("User not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("User not found")
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation error: {}", ex.getMessage());

        String details = ex.getBindingResult().getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Validation error")
                .error(ApiResponse.ErrorDetails.builder()
                    .code("VALIDATION_ERROR")
                    .message("Validation failed")
                    .details(details)
                    .build())
                .build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        log.error("Runtime exception occurred", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("An error occurred")
                .error(ApiResponse.ErrorDetails.builder()
                    .code("INTERNAL_ERROR")
                    .message(ex.getMessage())
                    .build())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, WebRequest request) {
        log.error("Unexpected exception occurred", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("An unexpected error occurred")
                .error(ApiResponse.ErrorDetails.builder()
                    .code("UNEXPECTED_ERROR")
                    .message(ex.getMessage())
                    .build())
                .build());
    }
}

