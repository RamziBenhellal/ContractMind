package com.ramzi.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeDto {

    private Long id;
    private BigDecimal amount;
    private String source;
    private Integer paydayOfMonth;
}
