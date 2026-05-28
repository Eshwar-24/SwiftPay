package com.hackathon.SwiftPay.exception;

public class UserNotFoundException extends PaymentException {

    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
}

