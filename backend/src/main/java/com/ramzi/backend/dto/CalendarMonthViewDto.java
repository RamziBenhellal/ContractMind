package com.ramzi.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record CalendarMonthViewDto(
        @JsonFormat(pattern = "yyyy-MM") YearMonth month,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate today,
        List<HistoricalDayDto> historicalDays,
        List<ProjectedDayDto> projectedDays
) {

    public record HistoricalDayDto(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            List<AccountTransactionGroupDto> actualTransactions,
            BigDecimal actualBalance,
            List<AccountBalanceDto> balancesByAccount,
            /** Kalender-Einträge (Verträge/Einkommen) – nur Anzeige, ändert actualBalance nicht. */
            List<CalendarOccurrenceDto> plannedEntries
    ) {
        public HistoricalDayDto {
            if (plannedEntries == null) {
                plannedEntries = List.of();
            }
        }
    }

    public record AccountTransactionGroupDto(
            Long accountId,
            String accountName,
            String iban,
            List<ActualTransactionDto> transactions,
            BigDecimal actualBalance
    ) {}

    public record ActualTransactionDto(
            Long id,
            Long accountId,
            String accountName,
            BigDecimal amount,
            String purpose,
            String counterpartyName,
            Long linkedContractId,
            Long linkedIncomeId
    ) {}

    public record AccountBalanceDto(
            Long accountId,
            String accountName,
            String iban,
            BigDecimal actualBalance
    ) {}

    public record ProjectedDayDto(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            List<CalendarOccurrenceDto> projectedEntries,
            BigDecimal projectedBalance
    ) {}
}
