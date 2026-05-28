package com.hackathon.SwiftPay.exception;

public class IdempotencyException extends PaymentException {

    public IdempotencyException(String message) {
        super("IDEMPOTENCY_VIOLATION", message);
    }
}

