package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankCalendarEntry;
import com.ramzi.backend.entity.CalendarEntryType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
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
class BankCalendarEntryRepositoryTest {

    @Autowired
    private BankCalendarEntryRepository calendarEntryRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesContractPaymentAndFindsByDateRange() {
        User user = persistUser("alice@test.de");
        Contract contract = contractRepository.save(Contract.builder()
                .user(user)
                .provider("Vodafone")
                .monthlyCost(new BigDecimal("39.99"))
                .dueDayOfMonth(15)
                .build());

        calendarEntryRepository.save(BankCalendarEntry.builder()
                .user(user)
                .title("Vodafone")
                .expectedDate(LocalDate.of(2026, 10, 15))
                .expectedAmount(new BigDecimal("-39.99"))
                .type(CalendarEntryType.CONTRACT_PAYMENT)
                .recurrenceRule("FREQ=MONTHLY;BYMONTHDAY=15")
                .sourceContract(contract)
                .build());

        assertThat(calendarEntryRepository.findByUser_Id(user.getId())).hasSize(1);
        assertThat(calendarEntryRepository.findByUser_IdAndType(user.getId(), CalendarEntryType.CONTRACT_PAYMENT))
                .extracting(BankCalendarEntry::getSourceContract)
                .extracting(Contract::getProvider)
                .containsExactly("Vodafone");
        assertThat(calendarEntryRepository.findByUser_IdAndExpectedDateBetween(
                user.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .hasSize(1);
        assertThat(calendarEntryRepository.findByUser_IdAndExpectedDateBetween(
                user.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isEmpty();
    }

    @Test
    void updatesAndDeletesIncomeCalendarEntry() {
        User user = persistUser("bob@test.de");
        Income income = incomeRepository.save(Income.builder()
                .user(user)
                .source("Gehalt")
                .amount(new BigDecimal("2800.00"))
                .paydayOfMonth(1)
                .build());

        BankCalendarEntry entry = calendarEntryRepository.save(BankCalendarEntry.builder()
                .user(user)
                .title("Gehalt")
                .expectedDate(LocalDate.of(2026, 10, 1))
                .expectedAmount(new BigDecimal("2800.00"))
                .type(CalendarEntryType.INCOME)
                .sourceIncome(income)
                .build());

        entry.setExpectedAmount(new BigDecimal("2900.00"));
        calendarEntryRepository.save(entry);

        BankCalendarEntry reloaded = calendarEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getExpectedAmount()).isEqualByComparingTo("2900.00");
        assertThat(reloaded.getSourceIncome().getId()).isEqualTo(income.getId());
        assertThat(calendarEntryRepository.findByUser_Email("bob@test.de")).hasSize(1);

        calendarEntryRepository.deleteById(entry.getId());
        assertThat(calendarEntryRepository.findById(entry.getId())).isEmpty();
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder().email(email).password("secret").build());
    }
}
