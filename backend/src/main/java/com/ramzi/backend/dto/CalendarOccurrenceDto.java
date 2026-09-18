package com.ramzi.backend.dto;

import com.ramzi.backend.entity.CalendarEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalendarOccurrenceDto(
        Long sourceEntryId,
        String title,
        LocalDate date,
        BigDecimal amount,
        CalendarEntryType type,
        String recurrenceRule,
        Long sourceContractId,
        Long sourceIncomeId,
        boolean occurred,
        Long matchedTransactionId
) {}
