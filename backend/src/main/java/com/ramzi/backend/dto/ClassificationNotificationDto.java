package com.ramzi.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClassificationNotificationDto(
        Long transactionId,
        boolean suggestNewContract,
        String title,
        BigDecimal amount,
        Instant createdAt
) {}
