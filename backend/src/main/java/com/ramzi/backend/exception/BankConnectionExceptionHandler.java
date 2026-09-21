package com.ramzi.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class BankConnectionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BankConnectionExceptionHandler.class);

    @ExceptionHandler(BankConnectionException.class)
    public ResponseEntity<Map<String, Object>> handle(BankConnectionException ex) {
        log.warn("BankConnectionException code={} status={}: {}", ex.getCode(), ex.getStatus().value(), ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        if (ex.getRetryAfterSeconds() != null) {
            body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null) {
            response.header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }
        return response.body(body);
    }
}
