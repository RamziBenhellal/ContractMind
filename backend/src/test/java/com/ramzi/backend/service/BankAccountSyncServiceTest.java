package com.ramzi.backend.service;

import com.ramzi.backend.dto.SyncNowResponse;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankConnection;
import com.ramzi.backend.entity.BankConnectionStatus;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.exception.BankConnectionException;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.service.fints.FinTsClient;
import com.ramzi.backend.service.fints.FinTsModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankAccountSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(5);
    private static final String EMAIL = "alice@test.de";

    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private FinTsClient finTsClient;
    @Mock
    private BankConnectionOrchestrationService orchestration;

    private BankAccountSyncService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new BankAccountSyncService(
                bankAccountRepository, finTsClient, orchestration, clock, MIN_INTERVAL);
    }

    @Test
    void allowsSyncWhenLastSyncIsExactlyTheMinimumIntervalAgo() {
        BankAccount account = account(NOW.minus(MIN_INTERVAL));
        stubSuccessfulFetch(account, 1);

        SyncNowResponse response = service.syncNow(EMAIL, 10L);

        assertThat(response.newTransactionCount()).isEqualTo(1);
        assertThat(response.syncedAt()).isEqualTo(NOW);
        verify(finTsClient).fetchTransactions("python-session");
    }

    @Test
    void rejectsSyncOneSecondBeforeTheInterval() {
        BankAccount account = account(NOW.minus(MIN_INTERVAL).plusSeconds(1));
        when(bankAccountRepository.findByIdAndUser_Email(10L, EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.syncNow(EMAIL, 10L))
                .isInstanceOf(BankConnectionException.class)
                .satisfies(error -> {
                    BankConnectionException ex = (BankConnectionException) error;
                    assertThat(ex.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                    assertThat(ex.getStatus().value()).isEqualTo(429);
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(1);
                });
        verify(finTsClient, never()).fetchTransactions(any());
    }

    @Test
    void allowsSyncOneSecondAfterTheInterval() {
        BankAccount account = account(NOW.minus(MIN_INTERVAL).minusSeconds(1));
        stubSuccessfulFetch(account, 0);

        SyncNowResponse response = service.syncNow(EMAIL, 10L);

        assertThat(response.newTransactionCount()).isEqualTo(0);
        verify(finTsClient).fetchTransactions("python-session");
    }

    @Test
    void reportsFullIntervalWhenSyncedJustNow() {
        assertThatThrownBy(() -> service.assertRateLimit(NOW, NOW))
                .isInstanceOf(BankConnectionException.class)
                .satisfies(error -> assertThat(((BankConnectionException) error).getRetryAfterSeconds())
                        .isEqualTo(300));
    }

    @Test
    void mapsBankUnreachableToBankUnavailable() {
        BankConnectionException mapped = BankAccountSyncService.mapFetchError(
                BankConnectionException.bankUnreachable("timeout"));
        assertThat(mapped.getCode()).isEqualTo("BANK_UNAVAILABLE");
        assertThat(mapped.getStatus().value()).isEqualTo(503);
    }

    @Test
    void mapsTanErrorsToTanRequired() {
        assertThat(BankAccountSyncService.mapFetchError(BankConnectionException.tanExpired()).getCode())
                .isEqualTo("TAN_REQUIRED");
        assertThat(BankAccountSyncService.mapFetchError(BankConnectionException.tanInvalid()).getCode())
                .isEqualTo("TAN_REQUIRED");
    }

    @Test
    void mapsMissingSessionToConnectionExpired() {
        BankAccount account = account(NOW.minus(Duration.ofHours(1)));
        account.setBankConnection(null);
        when(bankAccountRepository.findByIdAndUser_Email(10L, EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.syncNow(EMAIL, 10L))
                .isInstanceOf(BankConnectionException.class)
                .satisfies(error -> assertThat(((BankConnectionException) error).getCode())
                        .isEqualTo("CONNECTION_EXPIRED"));
    }

    @Test
    void mapsPythonNotFoundToConnectionExpired() {
        BankConnectionException mapped = BankAccountSyncService.mapFetchError(BankConnectionException.notFound());
        assertThat(mapped.getCode()).isEqualTo("CONNECTION_EXPIRED");
        assertThat(mapped.getStatus().value()).isEqualTo(410);
    }

    @Test
    void syncNowPropagatesMappedBankUnavailable() {
        BankAccount account = account(NOW.minus(Duration.ofHours(1)));
        when(bankAccountRepository.findByIdAndUser_Email(10L, EMAIL)).thenReturn(Optional.of(account));
        when(finTsClient.fetchTransactions("python-session"))
                .thenThrow(BankConnectionException.bankUnreachable());

        assertThatThrownBy(() -> service.syncNow(EMAIL, 10L))
                .isInstanceOf(BankConnectionException.class)
                .satisfies(error -> assertThat(((BankConnectionException) error).getCode())
                        .isEqualTo("BANK_UNAVAILABLE"));
        verify(orchestration, never()).applyFetchedAccounts(any(), any());
    }

    private void stubSuccessfulFetch(BankAccount account, int newTxCount) {
        when(bankAccountRepository.findByIdAndUser_Email(10L, EMAIL)).thenReturn(Optional.of(account));
        when(finTsClient.fetchTransactions("python-session"))
                .thenReturn(new FinTsModels.TransactionFetchResponse("python-session", List.of(), true));
        BankTransaction created = BankTransaction.builder()
                .id(1L)
                .bankAccount(account)
                .amount(new BigDecimal("-12.50"))
                .bookingDate(LocalDate.of(2026, 9, 18))
                .build();
        List<BankTransaction> createdList = newTxCount == 0 ? List.of() : List.of(created);
        when(orchestration.applyFetchedAccounts(eq(account.getBankConnection()), any())).thenReturn(createdList);
        BankAccount updated = account(NOW);
        updated.setBalance(new BigDecimal("500.00"));
        when(bankAccountRepository.findById(10L)).thenReturn(Optional.of(updated));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static BankAccount account(Instant lastSyncedAt) {
        User user = User.builder().id(1L).email(EMAIL).password("pw").build();
        BankConnection connection = BankConnection.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .user(user)
                .blz("25950130")
                .pythonSessionId("python-session")
                .status(BankConnectionStatus.ACTIVE)
                .encryptedCredentials("cipher")
                .build();
        return BankAccount.builder()
                .id(10L)
                .user(user)
                .accountName("Giro")
                .bankName("Sparkasse")
                .iban("DE2110010010123456781024")
                .balance(new BigDecimal("524.80"))
                .lastSyncedAt(lastSyncedAt)
                .bankConnection(connection)
                .build();
    }
}
