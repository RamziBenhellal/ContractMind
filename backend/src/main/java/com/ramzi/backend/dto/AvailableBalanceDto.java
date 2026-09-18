package com.ramzi.backend.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record AvailableBalanceDto(
        BigDecimal currentBalance,
        BigDecimal expectedIncome,
        BigDecimal outstandingCharges,
        BigDecimal availableBalance,
        YearMonth month
) {}
