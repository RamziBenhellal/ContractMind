package com.ramzi.backend.service;

import com.ramzi.backend.dto.AvailableBalanceDto;
import com.ramzi.backend.dto.CalendarOccurrenceDto;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankCalendarEntry;
import com.ramzi.backend.entity.BankTransaction;
import com.ramzi.backend.entity.CalendarEntryType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.BankCalendarEntryRepository;
import com.ramzi.backend.repository.BankTransactionRepository;
import com.ramzi.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankCalendarService {

    private final BankCalendarEntryRepository calendarEntryRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final RecurrenceCalculator recurrenceCalculator = new RecurrenceCalculator();

    public void ensureContractEntry(User user, Contract contract, LocalDate after) {
        if (contract == null || calendarEntryRepository.existsBySourceContract_Id(contract.getId())) {
            return;
        }
        LocalDate expected = nextMonthlyDate(after, contract.getDueDayOfMonth());
        calendarEntryRepository.save(BankCalendarEntry.builder()
                .user(user)
                .title(firstNonBlank(contract.getProvider(), "Vertrag"))
                .expectedDate(expected)
                .expectedAmount(signedAmount(contract.getMonthlyCost(), true))
                .type(CalendarEntryType.CONTRACT_PAYMENT)
                .recurrenceRule(monthlyRule(contract.getDueDayOfMonth()))
                .sourceContract(contract)
                .build());
    }

    public void ensureIncomeEntry(User user, Income income, LocalDate after) {
        if (income == null || calendarEntryRepository.existsBySourceIncome_Id(income.getId())) {
            return;
        }
        LocalDate expected = nextMonthlyDate(after, income.getPaydayOfMonth());
        calendarEntryRepository.save(BankCalendarEntry.builder()
                .user(user)
                .title(firstNonBlank(income.getSource(), "Einkommen"))
                .expectedDate(expected)
                .expectedAmount(signedAmount(income.getAmount(), false))
                .type(CalendarEntryType.INCOME)
                .recurrenceRule(monthlyRule(income.getPaydayOfMonth()))
                .sourceIncome(income)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CalendarOccurrenceDto> generateUpcomingEntries(Long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<BankTransaction> booked = transactionRepository
                .findByBankAccount_User_IdAndBookingDateBetween(userId, from, to);

        return calendarEntryRepository.findByUser_Id(userId).stream()
                .flatMap(entry -> recurrenceCalculator
                        .occurrenceInMonth(entry.getExpectedDate(), entry.getRecurrenceRule(), month)
                        .map(date -> toOccurrence(entry, date, today, booked))
                        .stream())
                .sorted(Comparator.comparing(CalendarOccurrenceDto::date)
                        .thenComparing(CalendarOccurrenceDto::title, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarOccurrenceDto> getCalendar(String email, YearMonth month) {
        return generateUpcomingEntries(requireUser(email).getId(), month);
    }

    @Transactional(readOnly = true)
    public AvailableBalanceDto availableBalance(String email) {
        User user = requireUser(email);
        YearMonth month = YearMonth.now(clock);
        List<CalendarOccurrenceDto> entries = generateUpcomingEntries(user.getId(), month);

        BigDecimal current = currentBalance(user.getId());
        BigDecimal expectedIncome = entries.stream()
                .filter(entry -> entry.type() == CalendarEntryType.INCOME && !entry.occurred())
                .map(entry -> entry.amount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingCharges = entries.stream()
                .filter(entry -> entry.type() == CalendarEntryType.CONTRACT_PAYMENT && !entry.occurred())
                .map(entry -> entry.amount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AvailableBalanceDto(
                current,
                expectedIncome,
                outstandingCharges,
                current.add(expectedIncome).subtract(outstandingCharges),
                month
        );
    }

    static LocalDate nextMonthlyDate(LocalDate after, Integer dayOfMonth) {
        LocalDate reference = after != null ? after : LocalDate.now();
        int day = dayOfMonth != null ? dayOfMonth : reference.getDayOfMonth();
        LocalDate candidate = clampDay(reference, day);
        if (!candidate.isAfter(reference)) {
            candidate = clampDay(reference.plusMonths(1), day);
        }
        return candidate;
    }

    private CalendarOccurrenceDto toOccurrence(
            BankCalendarEntry entry,
            LocalDate date,
            LocalDate today,
            List<BankTransaction> booked
    ) {
        BankTransaction match = findMatchingTransaction(entry, date, booked);
        boolean occurred = match != null || date.isBefore(today);
        return new CalendarOccurrenceDto(
                entry.getId(),
                entry.getTitle(),
                date,
                entry.getExpectedAmount(),
                entry.getType(),
                entry.getRecurrenceRule(),
                entry.getSourceContract() != null ? entry.getSourceContract().getId() : null,
                entry.getSourceIncome() != null ? entry.getSourceIncome().getId() : null,
                occurred,
                match != null ? match.getId() : null
        );
    }

    private static BankTransaction findMatchingTransaction(
            BankCalendarEntry entry,
            LocalDate date,
            List<BankTransaction> booked
    ) {
        return booked.stream()
                .filter(tx -> matchesSource(entry, tx))
                .min(Comparator.comparingLong(tx -> Math.abs(ChronoUnit.DAYS.between(tx.getBookingDate(), date))))
                .orElse(null);
    }

    private static boolean matchesSource(BankCalendarEntry entry, BankTransaction tx) {
        if (entry.getSourceContract() != null && tx.getLinkedContract() != null) {
            return entry.getSourceContract().getId().equals(tx.getLinkedContract().getId());
        }
        if (entry.getSourceIncome() != null && tx.getLinkedIncome() != null) {
            return entry.getSourceIncome().getId().equals(tx.getLinkedIncome().getId());
        }
        return false;
    }

    private BigDecimal currentBalance(Long userId) {
        return bankAccountRepository.findByUser_Id(userId).stream()
                .map(BankCalendarService::accountBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal accountBalance(BankAccount account) {
        if (account.getBalance() != null) {
            return account.getBalance();
        }
        Map<LocalDate, BigDecimal> history = account.getBalanceHistory();
        if (history == null || history.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return history.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(BigDecimal.ZERO);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private static LocalDate clampDay(LocalDate month, int day) {
        return month.withDayOfMonth(Math.min(day, month.lengthOfMonth()));
    }

    private static String monthlyRule(Integer dayOfMonth) {
        if (dayOfMonth == null) {
            return "FREQ=MONTHLY";
        }
        return "FREQ=MONTHLY;BYMONTHDAY=" + dayOfMonth;
    }

    private static BigDecimal signedAmount(BigDecimal amount, boolean outgoing) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal abs = amount.abs();
        return outgoing ? abs.negate() : abs;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
