package com.ramzi.backend.service;

import com.ramzi.backend.entity.BankCalendarEntry;
import com.ramzi.backend.entity.CalendarEntryType;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.BankCalendarEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BankCalendarService {

    private final BankCalendarEntryRepository calendarEntryRepository;

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

    static LocalDate nextMonthlyDate(LocalDate after, Integer dayOfMonth) {
        LocalDate reference = after != null ? after : LocalDate.now();
        int day = dayOfMonth != null ? dayOfMonth : reference.getDayOfMonth();
        LocalDate candidate = clampDay(reference, day);
        if (!candidate.isAfter(reference)) {
            candidate = clampDay(reference.plusMonths(1), day);
        }
        return candidate;
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
