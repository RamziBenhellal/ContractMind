package com.ramzi.backend.repository;

import com.ramzi.backend.entity.AccountType;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BankAccountRepositoryTest {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsAccountByUserAndIban() {
        User user = persistUser("alice@test.de");
        BankAccount saved = bankAccountRepository.save(account(user, "DE89370400440532013000", AccountType.GIROKONTO));

        assertThat(saved.getId()).isNotNull();
        assertThat(bankAccountRepository.findByUser_Id(user.getId())).hasSize(1);
        assertThat(bankAccountRepository.findByUser_IdAndIban(user.getId(), "DE89370400440532013000"))
                .isPresent()
                .get()
                .extracting(BankAccount::getAccountType, BankAccount::getBalance)
                .containsExactly(AccountType.GIROKONTO, new BigDecimal("100.00"));
    }

    @Test
    void updatesAndDeletesAccount() {
        User user = persistUser("bob@test.de");
        BankAccount account = bankAccountRepository.save(account(user, "DE12500105170648489890", AccountType.PAYPAL));

        account.setBalance(new BigDecimal("250.50"));
        account.setAccountType(AccountType.KRYPTO);
        bankAccountRepository.save(account);

        BankAccount reloaded = bankAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getAccountType()).isEqualTo(AccountType.KRYPTO);
        assertThat(reloaded.getBalance()).isEqualByComparingTo("250.50");

        bankAccountRepository.deleteById(account.getId());
        assertThat(bankAccountRepository.findById(account.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder().email(email).password("secret").build());
    }

    private static BankAccount account(User user, String iban, AccountType type) {
        return BankAccount.builder()
                .user(user)
                .accountName("Hauptkonto")
                .bankName("Testbank")
                .iban(iban)
                .accountType(type)
                .balance(new BigDecimal("100.00"))
                .lastSyncedAt(Instant.parse("2026-09-18T10:00:00Z"))
                .build();
    }
}
