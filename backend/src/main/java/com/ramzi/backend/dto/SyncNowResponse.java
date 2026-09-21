package com.ramzi.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SyncNowResponse(
        Instant syncedAt,
        int newTransactionCount,
        BigDecimal updatedBalance
) {}
