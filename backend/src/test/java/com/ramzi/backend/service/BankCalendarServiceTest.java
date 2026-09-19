package com.ramzi.backend.service;

import com.ramzi.backend.dto.AvailableBalanceDto;
import com.ramzi.backend.dto.CalendarMonthViewDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.AccountBalanceDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.ActualTransactionDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.HistoricalDayDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.ProjectedDayDto;
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

    @Test
    void reconstructsHistoricalBalanceBackwardsWithMultipleTransactionsOnSameDay() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        BankAccount giro = account(3L, user, "Giro", new BigDecimal("500.00"));
        BankAccount extra = account(4L, user, "Extra", new BigDecimal("200.00"));
        BankTransaction morning = tx(11L, giro, LocalDate.of(2026, 9, 10), new BigDecimal("-10.00"));
        BankTransaction noon = tx(12L, giro, LocalDate.of(2026, 9, 10), new BigDecimal("-15.00"));
        BankTransaction later = tx(13L, giro, LocalDate.of(2026, 9, 12), new BigDecimal("5.00"));
        BankTransaction extraTx = tx(14L, extra, LocalDate.of(2026, 9, 10), new BigDecimal("-20.00"));

        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(giro, extra));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(
                eq(1L), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 19))))
                .thenReturn(List.of(morning, noon, later, extraTx));

        CalendarMonthViewDto view = service.getMonthView(1L, YearMonth.of(2026, 9));

        assertThat(view.today()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(view.historicalDays()).hasSize(18);
        HistoricalDayDto day10 = historical(view.historicalDays(), LocalDate.of(2026, 9, 10));
        HistoricalDayDto day11 = historical(view.historicalDays(), LocalDate.of(2026, 9, 11));
        HistoricalDayDto day12 = historical(view.historicalDays(), LocalDate.of(2026, 9, 12));

        assertThat(day10.actualTransactions()).hasSize(2);
        assertThat(day10.actualTransactions().stream()
                .filter(group -> group.accountId().equals(3L))
                .flatMap(group -> group.transactions().stream())
                .map(ActualTransactionDto::amount))
                .containsExactly(new BigDecimal("-10.00"), new BigDecimal("-15.00"));
        assertThat(day10.actualBalance()).isEqualByComparingTo("695.00");
        assertThat(accountBalance(day10, 3L)).isEqualByComparingTo("495.00");
        assertThat(accountBalance(day10, 4L)).isEqualByComparingTo("200.00");

        assertThat(day11.actualTransactions()).isEmpty();
        assertThat(day11.actualBalance()).isEqualByComparingTo("695.00");
        assertThat(day12.actualBalance()).isEqualByComparingTo("700.00");
        assertThat(accountBalance(day12, 3L)).isEqualByComparingTo("500.00");
        assertThat(accountBalance(day12, 4L)).isEqualByComparingTo("200.00");
    }

    @Test
    void excludesRedeemedContractsFromProjectionZone() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        BankAccount giro = account(3L, user, "Giro", new BigDecimal("1000.00"));
        Contract netflix = contract(42L, user);
        Contract rent = contract(7L, user);
        BankCalendarEntry netflixEntry = entry(user, 10L, "Netflix", LocalDate.of(2026, 8, 18),
                new BigDecimal("-12.50"), CalendarEntryType.CONTRACT_PAYMENT,
                "FREQ=MONTHLY;BYMONTHDAY=25", netflix, null);
        BankCalendarEntry rentEntry = entry(user, 11L, "Miete", LocalDate.of(2026, 1, 30),
                new BigDecimal("-900.00"), CalendarEntryType.CONTRACT_PAYMENT,
                "FREQ=MONTHLY;BYMONTHDAY=30", rent, null);
        BankTransaction bookedNetflix = tx(99L, giro, LocalDate.of(2026, 9, 18), new BigDecimal("-12.50"));
        bookedNetflix.setLinkedContract(netflix);

        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(giro));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of(netflixEntry, rentEntry));
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of(bookedNetflix));

        CalendarMonthViewDto view = service.getMonthView(1L, YearMonth.of(2026, 9));

        assertThat(view.projectedDays().stream()
                .flatMap(day -> day.projectedEntries().stream())
                .map(CalendarOccurrenceDto::title))
                .containsExactly("Miete")
                .doesNotContain("Netflix");
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 25)).projectedEntries()).isEmpty();
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 30)).projectedEntries())
                .extracting(CalendarOccurrenceDto::sourceContractId)
                .containsExactly(7L);
    }

    @Test
    void accumulatesProjectedBalanceAcrossDays() {
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        BankAccount giro = account(3L, user, "Giro", new BigDecimal("1000.00"));
        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(giro));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of(
                entry(user, 10L, "Bonus", LocalDate.of(2026, 9, 20), new BigDecimal("200.00"),
                        CalendarEntryType.INCOME, "FREQ=MONTHLY;BYMONTHDAY=20", null, null),
                entry(user, 11L, "Miete", LocalDate.of(2026, 9, 25), new BigDecimal("-900.00"),
                        CalendarEntryType.CONTRACT_PAYMENT, "FREQ=MONTHLY;BYMONTHDAY=25", null, null)
        ));
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        CalendarMonthViewDto view = service.getMonthView(1L, YearMonth.of(2026, 9));

        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 19)).projectedBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 20)).projectedBalance())
                .isEqualByComparingTo("1200.00");
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 24)).projectedBalance())
                .isEqualByComparingTo("1200.00");
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 25)).projectedBalance())
                .isEqualByComparingTo("300.00");
        assertThat(projected(view.projectedDays(), LocalDate.of(2026, 9, 30)).projectedBalance())
                .isEqualByComparingTo("300.00");
    }

    @Test
    void firstDayOfMonthHasEmptyHistoricalZone() {
        BankCalendarService firstDay = serviceAt(LocalDate.of(2026, 9, 1));
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(
                account(3L, user, "Giro", new BigDecimal("1000.00"))
        ));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        CalendarMonthViewDto view = firstDay.getMonthView(1L, YearMonth.of(2026, 9));

        assertThat(view.historicalDays()).isEmpty();
        assertThat(view.projectedDays()).hasSize(30);
        assertThat(view.projectedDays().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(view.projectedDays().get(0).projectedBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void lastDayOfMonthHasEmptyProjectionZoneAfterToday() {
        BankCalendarService lastDay = serviceAt(LocalDate.of(2026, 9, 30));
        User user = User.builder().id(1L).email("alice@test.de").password("pw").build();
        when(bankAccountRepository.findByUser_Id(1L)).thenReturn(List.of(
                account(3L, user, "Giro", new BigDecimal("1000.00"))
        ));
        when(calendarEntryRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        CalendarMonthViewDto view = lastDay.getMonthView(1L, YearMonth.of(2026, 9));

        assertThat(view.historicalDays()).hasSize(29);
        assertThat(view.historicalDays().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(view.historicalDays().get(28).date()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(view.projectedDays()).extracting(ProjectedDayDto::date)
                .containsExactly(LocalDate.of(2026, 9, 30));
    }

    private BankCalendarService serviceAt(LocalDate date) {
        Clock clock = Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new BankCalendarService(
                calendarEntryRepository,
                bankAccountRepository,
                transactionRepository,
                userRepository,
                clock
        );
    }

    private static HistoricalDayDto historical(List<HistoricalDayDto> days, LocalDate date) {
        return days.stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow();
    }

    private static ProjectedDayDto projected(List<ProjectedDayDto> days, LocalDate date) {
        return days.stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow();
    }

    private static BigDecimal accountBalance(HistoricalDayDto day, Long accountId) {
        return day.balancesByAccount().stream()
                .filter(item -> accountId.equals(item.accountId()))
                .map(AccountBalanceDto::actualBalance)
                .findFirst()
                .orElseThrow();
    }

    private static BankAccount account(Long id, User user, String name, BigDecimal balance) {
        return BankAccount.builder()
                .id(id)
                .user(user)
                .accountName(name)
                .bankName("Bank")
                .balance(balance)
                .build();
    }

    private static BankTransaction tx(Long id, BankAccount account, LocalDate date, BigDecimal amount) {
        return BankTransaction.builder()
                .id(id)
                .bankAccount(account)
                .externalId("ext-" + id)
                .bookingDate(date)
                .amount(amount)
                .purpose("tx-" + id)
                .build();
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
