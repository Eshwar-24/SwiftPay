package com.hackathon.SwiftPay.exception;

public class PaymentException extends RuntimeException {

    private String code;

    public PaymentException(String message) {
        super(message);
        this.code = "PAYMENT_ERROR";
    }

    public PaymentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PaymentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

