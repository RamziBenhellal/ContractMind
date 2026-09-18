package com.ramzi.backend.exception;

import org.springframework.http.HttpStatus;

public class TransactionClassificationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public TransactionClassificationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static TransactionClassificationException notFound() {
        return new TransactionClassificationException(
                "TRANSACTION_NOT_FOUND",
                "Umsatz nicht gefunden.",
                HttpStatus.NOT_FOUND);
    }

    public static TransactionClassificationException badRequest(String message) {
        return new TransactionClassificationException(
                "INVALID_FEEDBACK",
                message,
                HttpStatus.BAD_REQUEST);
    }
}
