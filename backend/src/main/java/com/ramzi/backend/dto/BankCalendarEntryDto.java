package com.ramzi.backend.dto;

import com.ramzi.backend.entity.CalendarEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankCalendarEntryDto {
    private Long id;
    private Long userId;
    private String title;
    private LocalDate expectedDate;
    private BigDecimal expectedAmount;
    private CalendarEntryType type;
    private String recurrenceRule;
    private Long sourceContractId;
    private Long sourceIncomeId;
}
