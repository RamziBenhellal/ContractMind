package com.ramzi.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "bank_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bank_account_id", "external_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "EUR";

    @Column(nullable = false)
    private LocalDate bookingDate;

    private LocalDate valueDate;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    private String counterpartyName;
    private String counterpartyIban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionClassification classification = TransactionClassification.UNCLASSIFIED;

    private Double confidenceScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_contract_id")
    private Contract linkedContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_income_id")
    private Income linkedIncome;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClassificationStatus classificationStatus = ClassificationStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private boolean suggestNewContract = false;

    @PrePersist
    void onCreate() {
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }
        if (classification == null) {
            classification = TransactionClassification.UNCLASSIFIED;
        }
        if (classificationStatus == null) {
            classificationStatus = ClassificationStatus.PENDING;
        }
    }
}
