package com.ramzi.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "bank_accounts")
@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false)
    private String bankName;

    @Column(length = 34)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountType accountType = AccountType.GIROKONTO;

    @Column(precision = 12, scale = 2)
    private BigDecimal balance;

    private Instant lastSyncedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_connection_id")
    private BankConnection bankConnection;

    @ElementCollection
    @CollectionTable(
            name = "account_balance_history",
            joinColumns = @JoinColumn(name = "bank_account_id")
    )
    @MapKeyColumn(name = "record_date")
    @Column(name = "balance")
    @Builder.Default
    private Map<LocalDate, BigDecimal> balanceHistory = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    void onCreate() {
        if (accountType == null) {
            accountType = AccountType.GIROKONTO;
        }
    }
}
