package com.ramzi.backend.service.fints;

import com.fasterxml.jackson.annotation.JsonAlias;

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

    public record SessionStartResponse(
            @JsonAlias("session_id") String sessionId,
            @JsonAlias("bank_name") String bankName,
            @JsonAlias("tan_methods") List<TanMethod> tanMethods
    ) {}

    public record ConfirmTanResponse(List<FinTsAccount> accounts) {}

    public record SelectTanResponse(String hint, boolean decoupled, String challenge) {}

    public record FinTsAccount(
            String iban,
            @JsonAlias("account_type") String accountType,
            BigDecimal balance,
            @JsonAlias("bank_name") String bankName,
            List<FinTsTransaction> transactions
    ) {
        public FinTsAccount {
            if (transactions == null) {
                transactions = List.of();
            }
        }

        public FinTsAccount(String iban, String accountType, BigDecimal balance, String bankName) {
            this(iban, accountType, balance, bankName, List.of());
        }
    }

    public record SyncResponse(List<SyncedAccount> accounts) {}

    public record SyncedAccount(
            String iban,
            @JsonAlias("account_type") String accountType,
            BigDecimal balance,
            @JsonAlias("bank_name") String bankName,
            List<FinTsTransaction> transactions
    ) {}

    public record FinTsTransaction(
            @JsonAlias("external_id") String externalId,
            @JsonAlias("booking_date") LocalDate bookingDate,
            BigDecimal amount,
            String purpose,
            @JsonAlias("counterpart_name") String counterpartName,
            @JsonAlias("counterpart_iban") String counterpartIban
    ) {}

    public record TransactionFetchResponse(
            @JsonAlias("connection_id") String connectionId,
            List<SyncedAccount> accounts,
            @JsonAlias("forwarded_to_backend") boolean forwardedToBackend
    ) {}
}
