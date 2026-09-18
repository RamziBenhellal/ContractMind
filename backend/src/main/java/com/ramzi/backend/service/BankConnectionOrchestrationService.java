package com.ramzi.backend.service;

import com.ramzi.backend.dto.BankConnectionDto.*;
import com.ramzi.backend.entity.*;
import com.ramzi.backend.exception.BankConnectionException;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.BankConnectionRepository;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.UserRepository;
import com.ramzi.backend.service.crypto.CredentialEncryptionService;
import com.ramzi.backend.service.crypto.CredentialEncryptionService.CredentialsPayload;
import com.ramzi.backend.service.fints.FinTsClient;
import com.ramzi.backend.service.fints.FinTsModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConnectionOrchestrationService {

    private final FinTsClient finTsClient;
    private final CredentialEncryptionService encryptionService;
    private final BankConnectionRepository connectionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionClassificationService classificationService;

    public List<BankSearchItem> searchBanks(String query) {
        return finTsClient.searchBanks(query).stream()
                .map(bank -> new BankSearchItem(bank.blz(), bank.name(), bank.bic()))
                .toList();
    }

    /**
     * Leitet BLZ + Login an den Python-FinTS-Dienst weiter, speichert die
     * Verbindung als PENDING_TAN und legt die Zugangsdaten nur verschlüsselt ab.
     */
    @Transactional
    public StartConnectionResponse startConnection(String email, StartConnectionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FinTsModels.SessionStartResponse session =
                finTsClient.startSession(request.blz(), request.loginId(), request.pin());

        BankConnection connection = BankConnection.builder()
                .user(user)
                .blz(request.blz())
                .bankName(session.bankName())
                .encryptedCredentials(encryptionService.encryptCredentials(request.loginId(), request.pin()))
                .pythonSessionId(session.sessionId())
                .status(BankConnectionStatus.PENDING_TAN)
                .build();
        connectionRepository.save(connection);

        List<TanMethodDto> tanMethods = session.tanMethods() == null
                ? List.of()
                : session.tanMethods().stream()
                .map(method -> new TanMethodDto(method.id(), method.name(), method.hint()))
                .toList();

        return new StartConnectionResponse(connection.getId().toString(), tanMethods);
    }

    /**
     * Bestätigt die TAN, legt für jedes Konto ein BankAccount (Phase 1) an
     * und setzt den Connection-Status auf ACTIVE.
     */
    @Transactional
    public List<ConnectedAccountDto> confirmTan(String email, UUID connectionId, ConfirmTanRequest request) {
        BankConnection connection = connectionRepository.findByIdAndUser_Email(connectionId, email)
                .orElseThrow(BankConnectionException::notFound);

        if (connection.getStatus() != BankConnectionStatus.PENDING_TAN) {
            throw BankConnectionException.notPending();
        }

        List<FinTsModels.FinTsAccount> accounts = finTsClient.confirmTan(
                connection.getPythonSessionId(),
                request.tanMethodId(),
                request.tan()
        );

        connection.setSelectedTanMethodId(request.tanMethodId());
        connection.setStatus(BankConnectionStatus.ACTIVE);
        connection.setLastSyncedAt(Instant.now());
        connection.setLastSyncError(null);

        List<ConnectedAccountDto> result = new ArrayList<>();
        for (FinTsModels.FinTsAccount remote : accounts) {
            BankAccount persisted = upsertBankAccount(connection, remote);
            result.add(new ConnectedAccountDto(
                    persisted.getIban(),
                    remote.accountType(),
                    currentBalance(persisted, remote.balance())
            ));
        }
        return result;
    }

    /** Wird vom Scheduler alle 6 Stunden für jede ACTIVE Connection aufgerufen. */
    @Transactional
    public void syncAllActiveConnections() {
        List<BankConnection> active = connectionRepository.findByStatus(BankConnectionStatus.ACTIVE);
        log.info("Starte FinTS-Sync für {} aktive Verbindungen", active.size());
        for (BankConnection connection : active) {
            try {
                syncConnection(connection.getId());
            } catch (Exception e) {
                log.warn("Sync fehlgeschlagen für Connection {}: {}", connection.getId(), e.getMessage());
                connectionRepository.findById(connection.getId()).ifPresent(failed -> {
                    failed.setLastSyncError(trimError(e.getMessage()));
                    failed.setStatus(failed.getStatus());
                    connectionRepository.save(failed);
                });
            }
        }
    }

    @Transactional
    public void syncConnection(UUID connectionId) {
        BankConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(BankConnectionException::notFound);

        CredentialsPayload credentials = encryptionService.decryptCredentials(connection.getEncryptedCredentials());
        FinTsModels.SyncResponse sync = finTsClient.sync(connection.getBlz(), credentials.loginId(), credentials.pin());

        List<BankTransaction> created = new ArrayList<>();
        if (sync.accounts() != null) {
            for (FinTsModels.SyncedAccount remote : sync.accounts()) {
                BankAccount account = upsertBankAccount(connection, new FinTsModels.FinTsAccount(
                        remote.iban(), remote.accountType(), remote.balance(), remote.bankName()
                ));
                if (remote.transactions() != null) {
                    for (FinTsModels.FinTsTransaction tx : remote.transactions()) {
                        saveIfNew(account, tx).ifPresent(created::add);
                    }
                }
            }
        }

        connection.setLastSyncedAt(Instant.now());
        connection.setLastSyncError(null);
        connectionRepository.save(connection);

        if (!created.isEmpty()) {
            classificationService.classifyNewTransactions(created);
            transactionRepository.saveAll(created);
        }
    }

    private BankAccount upsertBankAccount(BankConnection connection, FinTsModels.FinTsAccount remote) {
        User user = connection.getUser();
        if (remote.iban() == null || remote.iban().isBlank()) {
            BankAccount account = new BankAccount();
            account.setUser(user);
            account.setBankConnection(connection);
            account.setAccountType(AccountType.fromBankLabel(remote.accountType()));
            account.setBankName(firstNonBlank(remote.bankName(), connection.getBankName(), connection.getBlz()));
            account.setAccountName(firstNonBlank(remote.accountType(), "Konto"));
            account.setBalance(remote.balance());
            account.setLastSyncedAt(Instant.now());
            if (remote.balance() != null) {
                account.setBalanceHistory(new java.util.HashMap<>());
                account.getBalanceHistory().put(LocalDate.now(), remote.balance());
            }
            return bankAccountRepository.save(account);
        }

        BankAccount account = bankAccountRepository.findByUser_IdAndIban(user.getId(), remote.iban())
                .orElseGet(BankAccount::new);

        account.setUser(user);
        account.setBankConnection(connection);
        account.setIban(remote.iban());
        account.setAccountType(AccountType.fromBankLabel(remote.accountType()));
        account.setBankName(firstNonBlank(remote.bankName(), connection.getBankName(), connection.getBlz()));
        account.setAccountName(buildAccountName(remote));
        account.setBalance(remote.balance());
        account.setLastSyncedAt(Instant.now());

        if (remote.balance() != null) {
            if (account.getBalanceHistory() == null) {
                account.setBalanceHistory(new java.util.HashMap<>());
            }
            account.getBalanceHistory().put(LocalDate.now(), remote.balance());
        }

        return bankAccountRepository.save(account);
    }

    private java.util.Optional<BankTransaction> saveIfNew(BankAccount account, FinTsModels.FinTsTransaction tx) {
        String externalId = deriveExternalId(tx);
        if (transactionRepository.existsByBankAccountAndExternalId(account, externalId)) {
            return java.util.Optional.empty();
        }
        LocalDate bookingDate = tx.bookingDate() != null ? tx.bookingDate() : LocalDate.now();
        BankTransaction created = BankTransaction.builder()
                .bankAccount(account)
                .externalId(externalId)
                .bookingDate(bookingDate)
                .valueDate(bookingDate)
                .amount(tx.amount() != null ? tx.amount() : BigDecimal.ZERO)
                .currency("EUR")
                .purpose(tx.purpose())
                .counterpartyName(tx.counterpartName())
                .counterpartyIban(tx.counterpartIban())
                .classification(TransactionClassification.UNCLASSIFIED)
                .classificationStatus(ClassificationStatus.PENDING)
                .build();
        return java.util.Optional.of(transactionRepository.save(created));
    }

    private static String deriveExternalId(FinTsModels.FinTsTransaction tx) {
        if (tx.externalId() != null && !tx.externalId().isBlank()) {
            return tx.externalId();
        }
        String raw = String.join("|",
                String.valueOf(tx.bookingDate()),
                String.valueOf(tx.amount()),
                nullToEmpty(tx.purpose()),
                nullToEmpty(tx.counterpartIban()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String buildAccountName(FinTsModels.FinTsAccount remote) {
        String type = firstNonBlank(remote.accountType(), "Konto");
        String iban = remote.iban();
        if (iban == null || iban.length() < 8) {
            return type;
        }
        return type + " •••• " + iban.substring(iban.length() - 4);
    }

    private static BigDecimal currentBalance(BankAccount account, BigDecimal fallback) {
        if (account.getBalance() != null) {
            return account.getBalance();
        }
        if (account.getBalanceHistory() != null && !account.getBalanceHistory().isEmpty()) {
            return account.getBalanceHistory().getOrDefault(LocalDate.now(), fallback);
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimError(String message) {
        if (message == null) return "unknown error";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
