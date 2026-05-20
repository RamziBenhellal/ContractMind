package com.ramzi.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contracts")
@Setter @Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String provider;
    private String contractType; // z.B. Internet, Strom
    private String status; // z.B. Verified, waiting for AI verification

    @Column( precision = 10, scale = 2)
    private BigDecimal monthlyCost;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate noticedPeriodDate;

    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String rawAiSummary; // Hier speichern wir später die KI-Analyse

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
