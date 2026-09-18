package com.ramzi.backend.dto;

import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.TransactionClassification;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class TransactionFeedbackDto {

    public enum Decision {
        EXISTING_MATCH,
        NEW_CONTRACT,
        NEW_INCOME,
        NORMAL
    }

    public record NewContractData(
            String provider,
            String contractType,
            BigDecimal monthlyCost,
            Integer dueDayOfMonth
    ) {}

    public record FeedbackRequest(
            @NotNull Decision decision,
            Long targetEntityId,
            NewContractData newContractData,
            String category
    ) {}

    public record PendingReviewDto(
            Long id,
            java.math.BigDecimal amount,
            String purpose,
            String counterpartyName,
            String counterpartyIban,
            TransactionClassification classification,
            Double confidenceScore,
            boolean suggestNewContract
    ) {}

    public record FeedbackResponse(
            Long id,
            TransactionClassification classification,
            ClassificationStatus classificationStatus,
            Long linkedContractId,
            Long linkedIncomeId,
            String category,
            boolean suggestNewContract
    ) {}
}
