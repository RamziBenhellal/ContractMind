package com.ramzi.backend.repository;

import com.ramzi.backend.entity.AccountType;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.TransactionClassification;
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
class BankTransactionRepositoryTest {

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesTransactionLinkedToContractAndFindsByAccount() {
        User user = persistUser("alice@test.de");
        BankAccount account = persistAccount(user);
        Contract contract = contractRepository.save(Contract.builder()
                .user(user)
                .provider("Netflix")
                .monthlyCost(new BigDecimal("12.50"))
                .build());

        BankTransaction saved = transactionRepository.save(BankTransaction.builder()
                .bankAccount(account)
                .externalId("tx-1")
                .amount(new BigDecimal("-12.50"))
                .currency("EUR")
                .bookingDate(LocalDate.of(2026, 9, 18))
                .valueDate(LocalDate.of(2026, 9, 18))
                .purpose("Netflix")
                .counterpartyName("Netflix International")
                .counterpartyIban("DE02120300000000202051")
                .classification(TransactionClassification.CONTRACT)
                .confidenceScore(0.91)
                .linkedContract(contract)
                .category("streaming")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(transactionRepository.existsByBankAccountAndExternalId(account, "tx-1")).isTrue();
        assertThat(transactionRepository.findByBankAccount_Id(account.getId())).hasSize(1);
        assertThat(transactionRepository.findByClassification(TransactionClassification.CONTRACT))
                .extracting(BankTransaction::getLinkedContract)
                .extracting(Contract::getId)
                .containsExactly(contract.getId());
    }

    @Test
    void updatesClassificationAndDeletesTransaction() {
        User user = persistUser("bob@test.de");
        BankAccount account = persistAccount(user);
        Income income = incomeRepository.save(Income.builder()
                .user(user)
                .source("Gehalt")
                .amount(new BigDecimal("2800.00"))
                .paydayOfMonth(1)
                .build());

        BankTransaction transaction = transactionRepository.save(BankTransaction.builder()
                .bankAccount(account)
                .externalId("tx-income")
                .amount(new BigDecimal("2800.00"))
                .bookingDate(LocalDate.of(2026, 9, 1))
                .purpose("Gehalt")
                .build());

        transaction.setClassification(TransactionClassification.INCOME);
        transaction.setLinkedIncome(income);
        transactionRepository.save(transaction);

        BankTransaction reloaded = transactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(reloaded.getCurrency()).isEqualTo("EUR");
        assertThat(reloaded.getClassification()).isEqualTo(TransactionClassification.INCOME);
        assertThat(reloaded.getLinkedIncome().getId()).isEqualTo(income.getId());
        assertThat(transactionRepository.findByBankAccount_User_Id(user.getId())).hasSize(1);

        transactionRepository.deleteById(transaction.getId());
        assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
        assertThat(bankAccountRepository.findById(account.getId())).isPresent();
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder().email(email).password("secret").build());
    }

    private BankAccount persistAccount(User user) {
        return bankAccountRepository.save(BankAccount.builder()
                .user(user)
                .accountName("Giro")
                .bankName("Testbank")
                .iban("DE89370400440532013000")
                .accountType(AccountType.GIROKONTO)
                .build());
    }
}
