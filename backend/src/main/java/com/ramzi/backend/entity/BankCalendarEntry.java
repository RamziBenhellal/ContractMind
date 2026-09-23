package com.ramzi.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_calendar_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate expectedDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalendarEntryType type;

    private String recurrenceRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_contract_id")
    private Contract sourceContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_income_id")
    private Income sourceIncome;
}
