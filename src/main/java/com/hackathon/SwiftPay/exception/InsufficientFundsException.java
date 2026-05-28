package com.hackathon.SwiftPay.exception;

public class InsufficientFundsException extends PaymentException {

    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message);
    }
}

