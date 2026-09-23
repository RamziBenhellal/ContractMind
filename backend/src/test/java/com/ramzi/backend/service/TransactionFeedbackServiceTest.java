package com.ramzi.backend.service;

import com.ramzi.backend.dto.TransactionFeedbackDto.Decision;
import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackRequest;
import com.ramzi.backend.dto.TransactionFeedbackDto.FeedbackResponse;
import com.ramzi.backend.dto.TransactionFeedbackDto.NewContractData;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationMatchType;
import com.ramzi.backend.entity.ClassificationRule;
import com.ramzi.backend.entity.ClassificationStatus;
import com.ramzi.backend.entity.ClassificationTargetType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.TransactionClassification;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.exception.TransactionClassificationException;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.ClassificationRuleRepository;
import com.ramzi.backend.repository.ContractRepository;
import com.ramzi.backend.repository.IncomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionFeedbackServiceTest {

    private static final String EMAIL = "alice@test.de";

    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private IncomeRepository incomeRepository;
    @Mock
    private ClassificationRuleRepository ruleRepository;
    @Mock
    private BankCalendarService calendarService;
    @Mock
    private ClassificationNotificationService notificationService;

    @InjectMocks
    private TransactionFeedbackService feedbackService;

    private User user;
    private BankTransaction transaction;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email(EMAIL).password("pw").build();
        BankAccount account = BankAccount.builder().id(10L).user(user).accountName("Giro").bankName("Bank").build();
        transaction = BankTransaction.builder()
                .id(100L)
                .bankAccount(account)
                .externalId("tx-1")
                .amount(new BigDecimal("-12.50"))
                .bookingDate(LocalDate.of(2026, 9, 18))
                .purpose("Netflix")
                .counterpartyName("Netflix International")
                .counterpartyIban("DE02120300000000202051")
                .suggestNewContract(true)
                .classificationStatus(ClassificationStatus.PENDING_REVIEW)
                .build();
        lenient().when(transactionRepository.findByIdAndBankAccount_User_Email(100L, EMAIL))
                .thenReturn(Optional.of(transaction));
        lenient().when(transactionRepository.save(any(BankTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void existingMatchLinksContractAndLearnsIbanRuleWithoutCalendar() {
        Contract contract = Contract.builder().id(42L).user(user).provider("Netflix").build();
        when(contractRepository.findById(42L)).thenReturn(Optional.of(contract));

        FeedbackResponse response = feedbackService.applyFeedback(
                EMAIL, 100L, new FeedbackRequest(Decision.EXISTING_MATCH, 42L, null, null));

        assertThat(response.classification()).isEqualTo(TransactionClassification.CONTRACT);
        assertThat(response.classificationStatus()).isEqualTo(ClassificationStatus.CLASSIFIED);
        assertThat(response.linkedContractId()).isEqualTo(42L);
        assertThat(response.suggestNewContract()).isFalse();

        ArgumentCaptor<ClassificationRule> ruleCaptor = ArgumentCaptor.forClass(ClassificationRule.class);
        verify(ruleRepository).save(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getMatchType()).isEqualTo(ClassificationMatchType.IBAN);
        assertThat(ruleCaptor.getValue().getMatchValue()).isEqualTo("DE02120300000000202051");
        assertThat(ruleCaptor.getValue().getTargetType()).isEqualTo(ClassificationTargetType.CONTRACT);
        assertThat(ruleCaptor.getValue().getTargetEntityId()).isEqualTo(42L);
        verify(calendarService, never()).ensureContractEntry(any(), any(), any());
        verify(notificationService).dismiss(EMAIL, 100L);
    }

    @Test
    void existingMatchRequiresTargetEntityId() {
        assertThatThrownBy(() -> feedbackService.applyFeedback(
                EMAIL, 100L, new FeedbackRequest(Decision.EXISTING_MATCH, null, null, null)))
                .isInstanceOf(TransactionClassificationException.class)
                .hasMessageContaining("targetEntityId");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void newContractCreatesEntityCalendarAndRule() {
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract created = invocation.getArgument(0);
            created.setId(7L);
            return created;
        });

        FeedbackResponse response = feedbackService.applyFeedback(
                EMAIL,
                100L,
                new FeedbackRequest(
                        Decision.NEW_CONTRACT,
                        null,
                        new NewContractData("Netflix", "Streaming", new BigDecimal("12.50"), 18),
                        "streaming"
                ));

        assertThat(response.classification()).isEqualTo(TransactionClassification.CONTRACT);
        assertThat(response.linkedContractId()).isEqualTo(7L);
        verify(calendarService).ensureContractEntry(eq(user), any(Contract.class), eq(LocalDate.of(2026, 9, 18)));
        verify(ruleRepository).save(any(ClassificationRule.class));
        verify(notificationService).dismiss(EMAIL, 100L);
    }

    @Test
    void newIncomeCreatesEntityCalendarAndRule() {
        transaction.setCounterpartyIban(null);
        when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> {
            Income created = invocation.getArgument(0);
            created.setId(3L);
            return created;
        });

        FeedbackResponse response = feedbackService.applyFeedback(
                EMAIL,
                100L,
                new FeedbackRequest(
                        Decision.NEW_INCOME,
                        null,
                        new NewContractData("Acme GmbH", null, new BigDecimal("2800.00"), 1),
                        null
                ));

        assertThat(response.classification()).isEqualTo(TransactionClassification.INCOME);
        assertThat(response.linkedIncomeId()).isEqualTo(3L);
        verify(calendarService).ensureIncomeEntry(eq(user), any(Income.class), eq(LocalDate.of(2026, 9, 18)));

        ArgumentCaptor<ClassificationRule> ruleCaptor = ArgumentCaptor.forClass(ClassificationRule.class);
        verify(ruleRepository).save(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getMatchType()).isEqualTo(ClassificationMatchType.MERCHANT_NAME_FUZZY);
        assertThat(ruleCaptor.getValue().getTargetType()).isEqualTo(ClassificationTargetType.INCOME);
        verify(calendarService, never()).ensureContractEntry(any(), any(), any());
    }

    @Test
    void normalSetsCategoryWithoutCalendarOrRule() {
        FeedbackResponse response = feedbackService.applyFeedback(
                EMAIL, 100L, new FeedbackRequest(Decision.NORMAL, null, null, "groceries"));

        assertThat(response.classification()).isEqualTo(TransactionClassification.NORMAL);
        assertThat(response.classificationStatus()).isEqualTo(ClassificationStatus.CLASSIFIED);
        assertThat(response.category()).isEqualTo("groceries");
        assertThat(response.linkedContractId()).isNull();
        verify(calendarService, never()).ensureContractEntry(any(), any(), any());
        verify(calendarService, never()).ensureIncomeEntry(any(), any(), any());
        verify(ruleRepository, never()).save(any());
        verify(contractRepository, never()).save(any());
        verify(incomeRepository, never()).save(any());
    }

    @Test
    void unknownTransactionIsNotFound() {
        when(transactionRepository.findByIdAndBankAccount_User_Email(999L, EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.applyFeedback(
                EMAIL, 999L, new FeedbackRequest(Decision.NORMAL, null, null, "other")))
                .isInstanceOf(TransactionClassificationException.class);
    }
}
