package com.ramzi.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "classification_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassificationMatchType matchType;

    @Column(nullable = false)
    private String matchValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassificationTargetType targetType;

    @Column(name = "target_entity_id", nullable = false)
    private Long targetEntityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learned_from_transaction_id")
    private BankTransaction learnedFromTransaction;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
