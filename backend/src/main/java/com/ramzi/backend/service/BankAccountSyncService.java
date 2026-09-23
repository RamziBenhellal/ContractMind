package com.ramzi.backend.service;

import com.ramzi.backend.dto.SyncNowResponse;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankConnection;
import com.ramzi.backend.entity.BankConnectionStatus;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.exception.BankConnectionException;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.service.fints.FinTsClient;
import com.ramzi.backend.service.fints.FinTsModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class BankAccountSyncService {

    private static final Logger log = LoggerFactory.getLogger(BankAccountSyncService.class);

    private final BankAccountRepository bankAccountRepository;
    private final FinTsClient finTsClient;
    private final BankConnectionOrchestrationService orchestration;
    private final Clock clock;
    private final Duration minSyncInterval;

    public BankAccountSyncService(
            BankAccountRepository bankAccountRepository,
            FinTsClient finTsClient,
            BankConnectionOrchestrationService orchestration,
            Clock clock,
            @Value("${fints.sync.min-interval:5m}") Duration minSyncInterval
    ) {
        this.bankAccountRepository = bankAccountRepository;
        this.finTsClient = finTsClient;
        this.orchestration = orchestration;
        this.clock = clock;
        this.minSyncInterval = minSyncInterval;
    }

    @Transactional
    public SyncNowResponse syncNow(String email, Long accountId) {
        BankAccount account = bankAccountRepository.findByIdAndUser_Email(accountId, email)
                .orElseThrow(BankConnectionException::notFound);

        Instant now = Instant.now(clock);
        assertRateLimit(account.getLastSyncedAt(), now);

        BankConnection connection = account.getBankConnection();
        if (connection == null
                || connection.getPythonSessionId() == null
                || connection.getPythonSessionId().isBlank()
                || connection.getStatus() != BankConnectionStatus.ACTIVE) {
            log.warn("sync-now accountId={} ohne aktive Verbindung → CONNECTION_EXPIRED", accountId);
            throw BankConnectionException.connectionExpired();
        }

        FinTsModels.TransactionFetchResponse fetched;
        try {
            log.info("sync-now accountId={} pythonSessionId={}", accountId, connection.getPythonSessionId());
            fetched = finTsClient.fetchTransactions(connection.getPythonSessionId());
        } catch (BankConnectionException e) {
            log.error("sync-now FinTS-Fehler accountId={} code={}: {}", accountId, e.getCode(), e.getMessage(), e);
            throw mapFetchError(e);
        } catch (RuntimeException e) {
            log.error("sync-now unerwarteter Fehler accountId={}", accountId, e);
            throw mapFetchError(e);
        }

        List<FinTsModels.SyncedAccount> remotes = fetched == null || fetched.accounts() == null
                ? List.of()
                : fetched.accounts();
        List<BankTransaction> created;
        try {
            created = orchestration.applyFetchedAccounts(connection, remotes);
        } catch (RuntimeException e) {
            log.error("sync-now Persistenz fehlgeschlagen accountId={}", accountId, e);
            throw BankConnectionException.unknown("Umsätze konnten nicht gespeichert werden: " + e.getMessage());
        }

        BankAccount updated = bankAccountRepository.findById(accountId).orElse(account);
        updated.setLastSyncedAt(now);
        updated = bankAccountRepository.save(updated);

        int newCount = (int) created.stream()
                .filter(tx -> tx.getBankAccount() != null && Objects.equals(tx.getBankAccount().getId(), accountId))
                .count();

        log.info("sync-now accountId={} ok newTransactions={}", accountId, newCount);
        return new SyncNowResponse(now, newCount, currentBalance(updated));
    }

    void assertRateLimit(Instant lastSyncedAt, Instant now) {
        if (lastSyncedAt == null) {
            return;
        }
        Duration elapsed = Duration.between(lastSyncedAt, now);
        if (elapsed.compareTo(minSyncInterval) >= 0) {
            return;
        }
        Duration remaining = minSyncInterval.minus(elapsed);
        int retryAfterSeconds = (int) Math.max(1L, (remaining.toMillis() + 999) / 1000);
        log.info("sync-now rate-limited retryAfterSeconds={}", retryAfterSeconds);
        throw BankConnectionException.rateLimited(retryAfterSeconds);
    }

    static BankConnectionException mapFetchError(RuntimeException error) {
        if (!(error instanceof BankConnectionException exception)) {
            return BankConnectionException.bankUnavailable(error.getMessage());
        }
        String code = exception.getCode();
        if ("TAN_INVALID".equals(code) || "TAN_EXPIRED".equals(code) || "TAN_REQUIRED".equals(code)) {
            return BankConnectionException.tanRequired();
        }
        if ("TIMEOUT".equals(code)) {
            return BankConnectionException.timeout();
        }
        if ("DECRYPTION_ERROR".equals(code)) {
            return BankConnectionException.decryptionError();
        }
        if (exception.getStatus() == HttpStatus.NOT_FOUND || "CONNECTION_EXPIRED".equals(code)) {
            return BankConnectionException.connectionExpired();
        }
        if ("INVALID_PIN".equals(code)) {
            return BankConnectionException.connectionExpired();
        }
        if ("BANK_UNAVAILABLE".equals(code) || "BANK_UNREACHABLE".equals(code)) {
            return BankConnectionException.bankUnavailable(exception.getMessage());
        }
        return BankConnectionException.bankUnavailable(exception.getMessage());
    }

    private static BigDecimal currentBalance(BankAccount account) {
        return account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
    }
}
