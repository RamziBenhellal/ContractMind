package com.ramzi.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 1, message = "Der Tag muss mindestens 1 sein")
    @Max(value = 31, message = "Der Tag darf maximal 31 sein")
    @Column(name = "due_day_of_month")
    private Integer dueDayOfMonth;

    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String rawAiSummary; // Hier speichern wir später die KI-Analyse

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
