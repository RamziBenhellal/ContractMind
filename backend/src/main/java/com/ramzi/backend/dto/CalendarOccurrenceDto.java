package com.ramzi.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ramzi.backend.entity.CalendarEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalendarOccurrenceDto(
        Long sourceEntryId,
        String title,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        BigDecimal amount,
        CalendarEntryType type,
        String recurrenceRule,
        Long sourceContractId,
        Long sourceIncomeId,
        boolean occurred,
        Long matchedTransactionId
) {}
