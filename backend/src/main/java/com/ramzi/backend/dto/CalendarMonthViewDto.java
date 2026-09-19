package com.ramzi.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record CalendarMonthViewDto(
        YearMonth month,
        LocalDate today,
        List<HistoricalDayDto> historicalDays,
        List<ProjectedDayDto> projectedDays
) {

    public record HistoricalDayDto(
            LocalDate date,
            List<AccountTransactionGroupDto> actualTransactions,
            BigDecimal actualBalance,
            List<AccountBalanceDto> balancesByAccount
    ) {}

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
            LocalDate date,
            List<CalendarOccurrenceDto> projectedEntries,
            BigDecimal projectedBalance
    ) {}
}
