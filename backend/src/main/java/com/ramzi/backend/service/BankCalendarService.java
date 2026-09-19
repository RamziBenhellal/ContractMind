package com.ramzi.backend.service;

import com.ramzi.backend.dto.AvailableBalanceDto;
import com.ramzi.backend.dto.CalendarMonthViewDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.AccountBalanceDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.AccountTransactionGroupDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.ActualTransactionDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.HistoricalDayDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.ProjectedDayDto;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    public CalendarMonthViewDto getMonthView(String email, YearMonth month) {
        return getMonthView(requireUser(email).getId(), month);
    }

    @Transactional(readOnly = true)
    public CalendarMonthViewDto getMonthView(Long userId, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        List<BankAccount> accounts = bankAccountRepository.findByUser_Id(userId).stream()
                .sorted(Comparator.comparing(BankAccount::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        LocalDate txFrom = monthStart.isAfter(today) ? today : monthStart;
        LocalDate txTo = today.isBefore(monthStart) ? monthStart : today;
        List<BankTransaction> booked = txFrom.isAfter(txTo)
                ? List.of()
                : transactionRepository.findByBankAccount_User_IdAndBookingDateBetween(userId, txFrom, txTo);
        List<BankCalendarEntry> templates = calendarEntryRepository.findByUser_Id(userId);

        return new CalendarMonthViewDto(
                month,
                today,
                buildHistoricalDays(monthStart, monthEnd, today, accounts, booked),
                buildProjectedDays(monthStart, monthEnd, today, accounts, booked, templates)
        );
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

    private List<HistoricalDayDto> buildHistoricalDays(
            LocalDate monthStart,
            LocalDate monthEnd,
            LocalDate today,
            List<BankAccount> accounts,
            List<BankTransaction> booked
    ) {
        List<HistoricalDayDto> days = new ArrayList<>();
        LocalDate end = monthEnd.isBefore(today) ? monthEnd : today.minusDays(1);
        if (end.isBefore(monthStart)) {
            return List.of();
        }
        for (LocalDate date = monthStart; !date.isAfter(end); date = date.plusDays(1)) {
            days.add(toHistoricalDay(date, today, accounts, booked));
        }
        return days;
    }

    private HistoricalDayDto toHistoricalDay(
            LocalDate date,
            LocalDate today,
            List<BankAccount> accounts,
            List<BankTransaction> booked
    ) {
        List<AccountTransactionGroupDto> groups = new ArrayList<>();
        List<AccountBalanceDto> balances = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BankAccount account : accounts) {
            BigDecimal endOfDay = balanceAtEndOfDay(account, date, today, booked);
            total = total.add(endOfDay);
            balances.add(new AccountBalanceDto(account.getId(), account.getAccountName(), account.getIban(), endOfDay));
            List<ActualTransactionDto> txs = booked.stream()
                    .filter(tx -> belongsTo(tx, account) && date.equals(tx.getBookingDate()))
                    .sorted(Comparator.comparing(BankTransaction::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(tx -> toActualTransaction(tx, account))
                    .toList();
            if (!txs.isEmpty()) {
                groups.add(new AccountTransactionGroupDto(
                        account.getId(), account.getAccountName(), account.getIban(), txs, endOfDay));
            }
        }
        return new HistoricalDayDto(date, groups, total, balances);
    }

    private List<ProjectedDayDto> buildProjectedDays(
            LocalDate monthStart,
            LocalDate monthEnd,
            LocalDate today,
            List<BankAccount> accounts,
            List<BankTransaction> booked,
            List<BankCalendarEntry> templates
    ) {
        LocalDate start = today.isAfter(monthStart) ? today : monthStart;
        if (start.isAfter(monthEnd)) {
            return List.of();
        }
        YearMonth month = YearMonth.from(monthStart);
        Set<Long> redeemedContractIds = booked.stream()
                .map(BankTransaction::getLinkedContract)
                .filter(Objects::nonNull)
                .map(Contract::getId)
                .collect(Collectors.toSet());
        Set<Long> redeemedIncomeIds = booked.stream()
                .map(BankTransaction::getLinkedIncome)
                .filter(Objects::nonNull)
                .map(Income::getId)
                .collect(Collectors.toSet());

        Map<LocalDate, List<CalendarOccurrenceDto>> byDate = new LinkedHashMap<>();
        for (BankCalendarEntry template : templates) {
            if (isRedeemed(template, redeemedContractIds, redeemedIncomeIds)) {
                continue;
            }
            recurrenceCalculator.occurrenceInMonth(template.getExpectedDate(), template.getRecurrenceRule(), month)
                    .filter(date -> !date.isBefore(start) && !date.isAfter(monthEnd))
                    .map(date -> toOccurrence(template, date, today, booked))
                    .ifPresent(occurrence -> byDate.computeIfAbsent(occurrence.date(), key -> new ArrayList<>()).add(occurrence));
        }

        BigDecimal running = accounts.stream()
                .map(BankCalendarService::accountBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ProjectedDayDto> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            List<CalendarOccurrenceDto> entries = byDate.getOrDefault(date, List.of()).stream()
                    .sorted(Comparator.comparing(CalendarOccurrenceDto::title, Comparator.nullsLast(String::compareTo)))
                    .toList();
            running = running.add(entries.stream()
                    .map(CalendarOccurrenceDto::amount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            days.add(new ProjectedDayDto(date, entries, running));
        }
        return days;
    }

    private static boolean isRedeemed(
            BankCalendarEntry entry,
            Set<Long> redeemedContractIds,
            Set<Long> redeemedIncomeIds
    ) {
        if (entry.getSourceContract() != null && redeemedContractIds.contains(entry.getSourceContract().getId())) {
            return true;
        }
        return entry.getSourceIncome() != null && redeemedIncomeIds.contains(entry.getSourceIncome().getId());
    }

    private static BigDecimal balanceAtEndOfDay(
            BankAccount account,
            LocalDate date,
            LocalDate today,
            List<BankTransaction> booked
    ) {
        BigDecimal later = booked.stream()
                .filter(tx -> belongsTo(tx, account))
                .filter(tx -> tx.getBookingDate() != null)
                .filter(tx -> tx.getBookingDate().isAfter(date) && !tx.getBookingDate().isAfter(today))
                .map(tx -> tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return accountBalance(account).subtract(later);
    }

    private static boolean belongsTo(BankTransaction tx, BankAccount account) {
        return tx.getBankAccount() != null && Objects.equals(tx.getBankAccount().getId(), account.getId());
    }

    private static ActualTransactionDto toActualTransaction(BankTransaction tx, BankAccount account) {
        return new ActualTransactionDto(
                tx.getId(),
                account.getId(),
                account.getAccountName(),
                tx.getAmount(),
                tx.getPurpose(),
                tx.getCounterpartyName(),
                tx.getLinkedContract() != null ? tx.getLinkedContract().getId() : null,
                tx.getLinkedIncome() != null ? tx.getLinkedIncome().getId() : null
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
