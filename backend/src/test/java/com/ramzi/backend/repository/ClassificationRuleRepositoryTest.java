package com.ramzi.backend.repository;

import com.ramzi.backend.entity.AccountType;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.ClassificationMatchType;
import com.ramzi.backend.entity.ClassificationRule;
import com.ramzi.backend.entity.ClassificationTargetType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ClassificationRuleRepositoryTest {

    @Autowired
    private ClassificationRuleRepository ruleRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesRuleLearnedFromTransaction() {
        User user = persistUser("alice@test.de");
        Contract contract = contractRepository.save(Contract.builder()
                .user(user)
                .provider("Netflix")
                .monthlyCost(new BigDecimal("12.50"))
                .build());
        BankTransaction transaction = persistTransaction(user);

        ClassificationRule saved = ruleRepository.save(ClassificationRule.builder()
                .user(user)
                .matchType(ClassificationMatchType.IBAN)
                .matchValue("DE02120300000000202051")
                .targetType(ClassificationTargetType.CONTRACT)
                .targetEntityId(contract.getId())
                .learnedFromTransaction(transaction)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(ruleRepository.findByUser_Id(user.getId())).hasSize(1);
        assertThat(ruleRepository.findByUser_IdAndMatchType(user.getId(), ClassificationMatchType.IBAN))
                .extracting(ClassificationRule::getTargetEntityId)
                .containsExactly(contract.getId());
        assertThat(ruleRepository.findByUser_IdAndMatchType(user.getId(), ClassificationMatchType.MERCHANT_NAME_FUZZY))
                .isEmpty();
    }

    @Test
    void updatesAndDeletesRule() {
        User user = persistUser("bob@test.de");
        ClassificationRule rule = ruleRepository.save(ClassificationRule.builder()
                .user(user)
                .matchType(ClassificationMatchType.MERCHANT_NAME_FUZZY)
                .matchValue("Netflx")
                .targetType(ClassificationTargetType.INCOME)
                .targetEntityId(99L)
                .build());

        rule.setMatchType(ClassificationMatchType.AMOUNT_RANGE);
        rule.setMatchValue("10.00-15.00");
        rule.setTargetType(ClassificationTargetType.CONTRACT);
        ruleRepository.save(rule);

        ClassificationRule reloaded = ruleRepository.findById(rule.getId()).orElseThrow();
        assertThat(reloaded.getMatchType()).isEqualTo(ClassificationMatchType.AMOUNT_RANGE);
        assertThat(reloaded.getMatchValue()).isEqualTo("10.00-15.00");
        assertThat(ruleRepository.findByUser_Email("bob@test.de")).hasSize(1);

        ruleRepository.deleteById(rule.getId());
        assertThat(ruleRepository.findById(rule.getId())).isEmpty();
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder().email(email).password("secret").build());
    }

    private BankTransaction persistTransaction(User user) {
        BankAccount account = bankAccountRepository.save(BankAccount.builder()
                .user(user)
                .accountName("Giro")
                .bankName("Testbank")
                .iban("DE89370400440532013000")
                .accountType(AccountType.GIROKONTO)
                .build());
        return transactionRepository.save(BankTransaction.builder()
                .bankAccount(account)
                .externalId("tx-learn")
                .amount(new BigDecimal("-12.50"))
                .bookingDate(LocalDate.of(2026, 9, 18))
                .purpose("Netflix")
                .build());
    }
}
