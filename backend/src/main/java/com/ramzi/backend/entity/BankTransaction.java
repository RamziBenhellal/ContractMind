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

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    private String counterpartName;
    private String counterpartIban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClassificationStatus classificationStatus = ClassificationStatus.PENDING;

    /** Wird in Phase 3 vom Klassifikator gesetzt */
    private String category;
}
