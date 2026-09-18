package com.ramzi.backend.dto;

import com.ramzi.backend.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankAccountDto {
    private Long id;
    private String bankName;
    private String accountName;
    private String iban;
    private AccountType accountType;
    private BigDecimal balance;
    private Instant lastSyncedAt;
    private Map<LocalDate, BigDecimal> balanceHistory;
}
