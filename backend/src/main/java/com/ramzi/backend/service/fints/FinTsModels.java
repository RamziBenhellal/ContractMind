package com.ramzi.backend.service.fints;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vertrag mit dem Python-FinTS-Dienst. Die Pfade liegen unter {@code /fints/...}.
 */
public final class FinTsModels {

    private FinTsModels() {}

    public record BankInfo(String blz, String name, String bic) {}

    public record TanMethod(String id, String name, String hint) {}

    public record SessionStartResponse(String sessionId, String bankName, List<TanMethod> tanMethods) {}

    public record ConfirmTanResponse(List<FinTsAccount> accounts) {}

    public record FinTsAccount(String iban, String accountType, BigDecimal balance, String bankName) {}

    public record SyncResponse(List<SyncedAccount> accounts) {}

    public record SyncedAccount(String iban, String accountType, BigDecimal balance, String bankName,
                                List<FinTsTransaction> transactions) {}

    public record FinTsTransaction(String externalId, LocalDate bookingDate, BigDecimal amount,
                                   String purpose, String counterpartName, String counterpartIban) {}
}
