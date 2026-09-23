package com.ramzi.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public class BankConnectionDto {

    public record BankSearchItem(String blz, String name, String bic) {}

    public record TanMethodDto(String id, String name, String hint) {}

    public record StartConnectionRequest(
            @NotBlank String blz,
            @NotBlank String loginId,
            @NotBlank String pin
    ) {}

    public record StartConnectionResponse(String connectionId, List<TanMethodDto> tanMethods) {}

    public record ConfirmTanRequest(
            String tanMethodId,
            String tan
    ) {}

    public record SelectTanMethodRequest(@NotBlank String tanMethodId) {}

    public record SelectTanMethodResponse(String hint, boolean decoupled, String challenge) {}

    public record ConnectedAccountDto(String iban, String accountType, BigDecimal balance) {}
}
