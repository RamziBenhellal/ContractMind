package com.ramzi.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiResponseDto {
    private String status;
    private String message;
    private AiContractData data;


    @Data
    public static class AiContractData {
        @JsonProperty("is_contract")
        private boolean isContract;
        private String provider;
        private String contractType;
        private BigDecimal monthlyCost;
        private String endDate;

    }
}
