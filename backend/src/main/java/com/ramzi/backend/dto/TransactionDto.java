package com.ramzi.backend.dto;

import com.ramzi.backend.entity.TransactionClassification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private Long id;
    private Long bankAccountId;
    private BigDecimal amount;
    private String currency;
    private LocalDate bookingDate;
    private LocalDate valueDate;
    private String purpose;
    private String counterpartyName;
    private String counterpartyIban;
    private TransactionClassification classification;
    private Double confidenceScore;
    private Long linkedContractId;
    private Long linkedIncomeId;
    private String category;
}
