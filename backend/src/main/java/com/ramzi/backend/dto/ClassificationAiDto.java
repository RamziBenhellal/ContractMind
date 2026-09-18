package com.ramzi.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ramzi.backend.entity.TransactionClassification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ClassificationAiDto {

    public enum SuggestedAction {
        AUTO_CALENDAR,
        ASK_USER,
        SUGGEST_NEW_CONTRACT
    }

    public record ExistingContract(
            Long id,
            String provider,
            BigDecimal monthlyCost,
            String counterpartyIban,
            String counterpartyName
    ) {}

    public record ExistingIncome(
            Long id,
            String source,
            BigDecimal amount
    ) {}

    public record ExistingRule(
            String matchType,
            String matchValue,
            String targetType,
            Long targetEntityId
    ) {}

    public record HistoryTransaction(
            BigDecimal amount,
            String counterpartyIban,
            LocalDate bookingDate,
            Long linkedContractId
    ) {}

    public record ClassifyRequest(
            BigDecimal amount,
            String purpose,
            String counterpartyName,
            String counterpartyIban,
            LocalDate bookingDate,
            @JsonProperty("existing_contracts") List<ExistingContract> existingContracts,
            @JsonProperty("existing_incomes") List<ExistingIncome> existingIncomes,
            @JsonProperty("existing_rules") List<ExistingRule> existingRules,
            @JsonProperty("transactionHistory") List<HistoryTransaction> transactionHistory
    ) {}

    public record ClassifyResponse(
            TransactionClassification classification,
            Double confidence,
            Long matchedEntityId,
            SuggestedAction suggestedAction
    ) {}
}
