package com.ramzi.backend.service;

import com.ramzi.backend.dto.AvailableBalanceDto;
import com.ramzi.backend.dto.CalendarOccurrenceDto;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankCalendarEntry;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.CalendarEntryType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.BankCalendarEntryRepository;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankCalendarServiceTest {

    @Mock
    private BankCalendarEntryRepository calendarEntryRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;

    private BankCalendarService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 19).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new BankCalendarService(
                calendarEntryRepository,
                bankAccountRepository,
                transactionRepository,
                userRepository,
                clock
        );
    }

    @Test
    void generateUpcomingEntriesExpandsMonthlyRuleAndMarksPastDaysOccurred() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        BankCalendarEntry rent = entry(user, 10L, "Miete", LocalDate.of(2026, 1, 31),
                new BigDecimal("-900.00"), CalendarEntryType.CONTRACT_PAYMENT,
                "FREQ=MONTHLY;BYMONTHDAY=31", contract(5L, user), null);
        BankCalendarEntry salary = entry(user, 11L, "Gehalt", LocalDate.of(2026, 1, 1),
                new BigDecimal("2800.00"), CalendarEntryType.INCOME,
                "FREQ=MONTHLY;BYMONTHDAY=1", null, null);

        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of(rent, salary));
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(
                eq(1L), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(List.of());

        List<CalendarOccurrenceDto> september = service.generateUpcomingEntries(1L, YearMonth.of(2026, 9));

        assertThat(september).extracting(CalendarOccurrenceDto::date)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(september.get(0).occurred()).isTrue();
        assertThat(september.get(1).occurred()).isFalse();
        assertThat(september.get(1).date()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void matchingBookedTransactionMarksOccurrenceAsOccurred() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        Contract netflix = contract(42L, user);
        BankCalendarEntry template = entry(user, 10L, "Netflix", LocalDate.of(2026, 8, 18),
                new BigDecimal("-12.50"), CalendarEntryType.CONTRACT_PAYMENT,
                "FREQ=MONTHLY;BYMONTHDAY=18", netflix, null);
        BankTransaction booked = BankTransaction.builder()
                .id(99L)
                .bookingDate(LocalDate.of(2026, 9, 18))
                .amount(new BigDecimal("-12.50"))
                .linkedContract(netflix)
                .build();

        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of(template));
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of(booked));

        List<CalendarOccurrenceDto> september = service.generateUpcomingEntries(1L, YearMonth.of(2026, 9));

        assertThat(september).hasSize(1);
        assertThat(september.get(0).occurred()).isTrue();
        assertThat(september.get(0).matchedTransactionId()).isEqualTo(99L);
    }

    @Test
    void availableBalanceAddsOpenIncomeAndSubtractsOpenCharges() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        when(userRepository.findByEmail("alice@test.de")).thenReturn(Optional.of(user));
        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(
                BankAccount.builder().id(3L).user(user).accountName("Giro").bankName("Bank")
                        .balance(new BigDecimal("1000.00")).build()
        ));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of(
                entry(user, 10L, "Miete", LocalDate.of(2026, 9, 30), new BigDecimal("-900.00"),
                        CalendarEntryType.CONTRACT_PAYMENT, "FREQ=MONTHLY;BYMONTHDAY=30", null, null),
                entry(user, 11L, "Bonus", LocalDate.of(2026, 9, 25), new BigDecimal("200.00"),
                        CalendarEntryType.INCOME, "FREQ=MONTHLY;BYMONTHDAY=25", null, null),
                entry(user, 12L, "Gehalt", LocalDate.of(2026, 9, 1), new BigDecimal("2800.00"),
                        CalendarEntryType.INCOME, "FREQ=MONTHLY;BYMONTHDAY=1", null, null)
        ));
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        AvailableBalanceDto result = service.availableBalance("alice@test.de");

        assertThat(result.currentBalance()).isEqualByComparingTo("1000.00");
        assertThat(result.expectedIncome()).isEqualByComparingTo("200.00");
        assertThat(result.outstandingCharges()).isEqualByComparingTo("900.00");
        assertThat(result.availableBalance()).isEqualByComparingTo("300.00");
        assertThat(result.month()).isEqualTo(YearMonth.of(2026, 9));
    }

    private static BankCalendarEntry entry(
            User user,
            Long id,
            String title,
            LocalDate expectedDate,
            BigDecimal amount,
            CalendarEntryType type,
            String rule,
            Contract contract,
            Long ignored
    ) {
        BankCalendarEntry entry = BankCalendarEntry.builder()
                .id(id)
                .user(user)
                .title(title)
                .expectedDate(expectedDate)
                .expectedAmount(amount)
                .type(type)
                .recurrenceRule(rule)
                .sourceContract(contract)
                .build();
        return entry;
    }

    private static Contract contract(Long id, User user) {
        return Contract.builder().id(id).user(user).provider("Netflix").build();
    }
}
