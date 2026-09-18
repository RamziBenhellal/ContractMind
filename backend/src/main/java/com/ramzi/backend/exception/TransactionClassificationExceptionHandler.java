package com.ramzi.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class TransactionClassificationExceptionHandler {

    @ExceptionHandler(TransactionClassificationException.class)
    public ResponseEntity<Map<String, String>> handle(TransactionClassificationException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of(
                        "code", ex.getCode(),
                        "message", ex.getMessage()
                ));
    }
}
