package com.ramzi.backend.service;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.ramzi.backend.entity.AccountType;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.CalendarEntryType;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.TransactionClassification;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.BankCalendarEntryRepository;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionClassificationIT {

    private static final String EMAIL = "alice@classify.test";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void aiServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("ai-service.url", wireMock::baseUrl);
    }

    @Autowired
    private TransactionClassificationService classificationService;

    @Autowired
    private ClassificationNotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private BankCalendarEntryRepository calendarEntryRepository;

    @AfterEach
    void tearDown() {
        calendarEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        contractRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userRepository.deleteAll();
        wireMock.resetAll();
    }

    @Test
    void autoCalendarLinksTransactionAndCreatesCalendarEntry() {
        Fixture fixture = persistFixture();
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .willReturn(okJson("""
                        {"classification":"CONTRACT","confidence":0.97,"matchedEntityId":%d,"suggestedAction":"AUTO_CALENDAR"}
                        """.formatted(fixture.contract().getId()))));

        classificationService.classifyNewTransactions(List.of(fixture.transaction()));

        BankTransaction reloaded = transactionRepository.findById(fixture.transaction().getId()).orElseThrow();
        assertThat(reloaded.getClassification()).isEqualTo(TransactionClassification.CONTRACT);
        assertThat(reloaded.getClassificationStatus()).isEqualTo(ClassificationStatus.CLASSIFIED);
        assertThat(reloaded.getLinkedContract().getId()).isEqualTo(fixture.contract().getId());
        assertThat(reloaded.getConfidenceScore()).isEqualTo(0.97);
        assertThat(reloaded.isSuggestNewContract()).isFalse();
        assertThat(calendarEntryRepository.findByUser_Email(EMAIL)).hasSize(1);
        assertThat(calendarEntryRepository.findByUser_Email(EMAIL).get(0).getType())
                .isEqualTo(CalendarEntryType.CONTRACT_PAYMENT);
        wireMock.verify(postRequestedFor(urlEqualTo("/classify-transaction")));
    }

    @Test
    void askUserMarksPendingReviewAndPublishesNotification() {
        Fixture fixture = persistFixture();
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .willReturn(okJson("""
                        {"classification":"UNCLASSIFIED","confidence":0.41,"suggestedAction":"ASK_USER"}
                        """)));

        classificationService.classifyNewTransactions(List.of(fixture.transaction()));

        BankTransaction reloaded = transactionRepository.findById(fixture.transaction().getId()).orElseThrow();
        assertThat(reloaded.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING_REVIEW);
        assertThat(reloaded.isSuggestNewContract()).isFalse();
        assertThat(notificationService.findByEmail(EMAIL)).hasSize(1);
        assertThat(notificationService.findByEmail(EMAIL).get(0).transactionId())
                .isEqualTo(fixture.transaction().getId());
        assertThat(calendarEntryRepository.findAll()).isEmpty();
    }

    @Test
    void suggestNewContractSetsReviewFlag() {
        Fixture fixture = persistFixture();
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .willReturn(okJson("""
                        {"classification":"CONTRACT","confidence":0.62,"suggestedAction":"SUGGEST_NEW_CONTRACT"}
                        """)));

        classificationService.classifyNewTransactions(List.of(fixture.transaction()));

        BankTransaction reloaded = transactionRepository.findById(fixture.transaction().getId()).orElseThrow();
        assertThat(reloaded.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING_REVIEW);
        assertThat(reloaded.isSuggestNewContract()).isTrue();
        assertThat(notificationService.findByEmail(EMAIL).get(0).suggestNewContract()).isTrue();
    }

    @Test
    void retriesTransientAiFailureThenClassifies() {
        Fixture fixture = persistFixture();
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("""
                        {"classification":"CONTRACT","confidence":0.96,"matchedEntityId":%d,"suggestedAction":"AUTO_CALENDAR"}
                        """.formatted(fixture.contract().getId()))));

        classificationService.classifyNewTransactions(List.of(fixture.transaction()));

        BankTransaction reloaded = transactionRepository.findById(fixture.transaction().getId()).orElseThrow();
        assertThat(reloaded.getClassificationStatus()).isEqualTo(ClassificationStatus.CLASSIFIED);
        wireMock.verify(2, postRequestedFor(urlEqualTo("/classify-transaction")));
    }

    @Test
    void aiOutageLeavesTransactionPending() {
        Fixture fixture = persistFixture();
        wireMock.stubFor(post(urlEqualTo("/classify-transaction"))
                .willReturn(aResponse().withStatus(503)));

        classificationService.classifyNewTransactions(List.of(fixture.transaction()));

        BankTransaction reloaded = transactionRepository.findById(fixture.transaction().getId()).orElseThrow();
        assertThat(reloaded.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING);
        assertThat(reloaded.getLinkedContract()).isNull();
    }

    private Fixture persistFixture() {
        User user = userRepository.save(User.builder().email(EMAIL).password("{noop}unused").build());
        BankAccount account = bankAccountRepository.save(BankAccount.builder()
                .user(user)
                .accountName("Giro")
                .bankName("Testbank")
                .iban("DE89370400440532013000")
                .accountType(AccountType.GIROKONTO)
                .build());
        Contract contract = contractRepository.save(Contract.builder()
                .user(user)
                .provider("Netflix")
                .monthlyCost(new BigDecimal("12.50"))
                .dueDayOfMonth(18)
                .status("VERIFIED")
                .build());
        BankTransaction transaction = transactionRepository.save(BankTransaction.builder()
                .bankAccount(account)
                .externalId("tx-classify-1")
                .amount(new BigDecimal("-12.50"))
                .currency("EUR")
                .bookingDate(LocalDate.of(2026, 9, 18))
                .purpose("Netflix")
                .counterpartyName("Netflix International")
                .counterpartyIban("DE02120300000000202051")
                .build());
        return new Fixture(contract, transaction);
    }

    private record Fixture(Contract contract, BankTransaction transaction) {}
}
