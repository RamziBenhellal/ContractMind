package com.ramzi.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bank_connections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 8)
    private String blz;

    private String bankName;

    /** AES-GCM-Ciphertext von loginId + PIN, niemals im Klartext speichern */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedCredentials;

    /** Session-Id des Python-FinTS-Dienstes, gültig bis zur TAN-Bestätigung */
    private String pythonSessionId;

    private String selectedTanMethodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankConnectionStatus status;

    private Instant lastSyncedAt;

    @Column(columnDefinition = "TEXT")
    private String lastSyncError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "bankConnection")
    private List<BankAccount> accounts = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = BankConnectionStatus.PENDING_TAN;
        }
    }
}
