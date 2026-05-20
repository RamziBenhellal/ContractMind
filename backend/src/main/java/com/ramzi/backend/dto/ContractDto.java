package com.ramzi.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractDto {

    private Long id;
    private String provider;
    private String contractType;
    private BigDecimal monthlyCost;
    private LocalDate endDate;
    private String filePath;
    private String status;

}
