package com.ramzi.backend.controller;

import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankConnection;
import com.ramzi.backend.entity.BankConnectionStatus;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.exception.BankConnectionException;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.BankConnectionRepository;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.UserRepository;
import com.ramzi.backend.service.BankConnectionOrchestrationService;
import com.ramzi.backend.service.fints.FinTsClient;
import com.ramzi.backend.service.fints.FinTsModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankConnectionOnboardingTest {

    private static final String EMAIL = "alice@test.de";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankConnectionRepository connectionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private BankConnectionOrchestrationService orchestrationService;

    @MockitoBean
    private FinTsClient finTsClient;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .email(EMAIL)
                .password("{noop}unused")
                .build());
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        bankAccountRepository.deleteAll();
        connectionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void completesOnboardingAndPersistsEncryptedCredentialsAndAccounts() throws Exception {
        when(finTsClient.startSession("50050201", "1234567", "geheim"))
                .thenReturn(new FinTsModels.SessionStartResponse(
                        "sess-1",
                        "Frankfurter Sparkasse",
                        List.of(new FinTsModels.TanMethod("chipTAN", "chipTAN", "Bestätige in deiner chipTAN-App"))
                ));
        when(finTsClient.confirmTan("sess-1", "chipTAN", "847291"))
                .thenReturn(List.of(
                        new FinTsModels.FinTsAccount(
                                "DE89370400440532013000",
                                "Girokonto",
                                new BigDecimal("1420.50"),
                                "Frankfurter Sparkasse")
                ));

        MvcResult start = mockMvc.perform(post("/api/bank-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blz":"50050201","loginId":"1234567","pin":"geheim"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").isNotEmpty())
                .andExpect(jsonPath("$.tanMethods[0].id").value("chipTAN"))
                .andReturn();

        String connectionId = connectionIdFrom(start);

        BankConnection pending = connectionRepository.findById(UUID.fromString(connectionId)).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(BankConnectionStatus.PENDING_TAN);
        assertThat(pending.getEncryptedCredentials()).isNotBlank();
        assertThat(pending.getEncryptedCredentials()).doesNotContain("geheim");
        assertThat(pending.getEncryptedCredentials()).doesNotContain("1234567");
        assertThat(pending.getBlz()).isEqualTo("50050201");

        mockMvc.perform(post("/api/bank-connections/{id}/confirm-tan", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tanMethodId":"chipTAN","tan":"847291"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban").value("DE89370400440532013000"))
                .andExpect(jsonPath("$[0].accountType").value("Girokonto"))
                .andExpect(jsonPath("$[0].balance").value(1420.50));

        BankConnection active = connectionRepository.findById(UUID.fromString(connectionId)).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(BankConnectionStatus.ACTIVE);

        List<BankAccount> accounts = bankAccountRepository.findByUser_Email(EMAIL);
        assertThat(accounts).hasSize(1);
        BankAccount account = accounts.get(0);
        assertThat(account.getIban()).isEqualTo("DE89370400440532013000");
        assertThat(account.getBankName()).isEqualTo("Frankfurter Sparkasse");
        assertThat(account.getAccountType()).isEqualTo("Girokonto");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void rejectsInvalidPinAndDoesNotStoreAConnection() throws Exception {
        when(finTsClient.startSession(any(), any(), any()))
                .thenThrow(BankConnectionException.invalidPin());

        mockMvc.perform(post("/api/bank-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blz":"50050201","loginId":"1234567","pin":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        assertThat(connectionRepository.findAll()).isEmpty();
        assertThat(bankAccountRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void keepsPendingTanWhenTanIsInvalid() throws Exception {
        when(finTsClient.startSession(eq("50050201"), eq("1234567"), eq("geheim")))
                .thenReturn(new FinTsModels.SessionStartResponse(
                        "sess-1", "Frankfurter Sparkasse",
                        List.of(new FinTsModels.TanMethod("chipTAN", "chipTAN", null))
                ));
        when(finTsClient.confirmTan("sess-1", "chipTAN", "000000"))
                .thenThrow(BankConnectionException.tanInvalid());

        MvcResult start = mockMvc.perform(post("/api/bank-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blz":"50050201","loginId":"1234567","pin":"geheim"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String connectionId = connectionIdFrom(start);

        mockMvc.perform(post("/api/bank-connections/{id}/confirm-tan", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tanMethodId":"chipTAN","tan":"000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TAN_INVALID"));

        BankConnection connection = connectionRepository.findById(UUID.fromString(connectionId)).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(BankConnectionStatus.PENDING_TAN);
        assertThat(bankAccountRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void syncStoresNewTransactionsAndTriggersClassification() throws Exception {
        when(finTsClient.startSession(any(), any(), any()))
                .thenReturn(new FinTsModels.SessionStartResponse(
                        "sess-1", "Frankfurter Sparkasse",
                        List.of(new FinTsModels.TanMethod("chipTAN", "chipTAN", null))
                ));
        when(finTsClient.confirmTan(any(), any(), any()))
                .thenReturn(List.of(new FinTsModels.FinTsAccount(
                        "DE89370400440532013000", "Girokonto", new BigDecimal("1420.50"), null)));
        when(finTsClient.sync(eq("50050201"), eq("1234567"), eq("geheim")))
                .thenReturn(new FinTsModels.SyncResponse(List.of(
                        new FinTsModels.SyncedAccount(
                                "DE89370400440532013000",
                                "Girokonto",
                                new BigDecimal("1408.00"),
                                "Frankfurter Sparkasse",
                                List.of(new FinTsModels.FinTsTransaction(
                                        "tx-1",
                                        LocalDate.of(2026, 9, 18),
                                        new BigDecimal("-12.50"),
                                        "Netflix",
                                        "Netflix International",
                                        null
                                ))
                        )
                )));

        MvcResult start = mockMvc.perform(post("/api/bank-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blz":"50050201","loginId":"1234567","pin":"geheim"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String connectionId = connectionIdFrom(start);

        mockMvc.perform(post("/api/bank-connections/{id}/confirm-tan", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tanMethodId":"chipTAN","tan":"847291"}
                                """))
                .andExpect(status().isOk());

        orchestrationService.syncConnection(UUID.fromString(connectionId));

        List<BankTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        BankTransaction tx = transactions.get(0);
        assertThat(tx.getExternalId()).isEqualTo("tx-1");
        assertThat(tx.getAmount()).isEqualByComparingTo("-12.50");
        assertThat(tx.getPurpose()).isEqualTo("Netflix");
        assertThat(tx.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING);
    }

    private static String connectionIdFrom(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"connectionId\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("connectionId missing in: " + body);
        }
        return matcher.group(1);
    }
}
