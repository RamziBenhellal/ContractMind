package com.ramzi.backend.event;

import java.math.BigDecimal;

public record ClassificationReviewNeededEvent(
        Long userId,
        String userEmail,
        Long transactionId,
        boolean suggestNewContract,
        String title,
        BigDecimal amount
) {}
