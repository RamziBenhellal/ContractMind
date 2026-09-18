package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankCalendarEntry;
import com.ramzi.backend.entity.CalendarEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BankCalendarEntryRepository extends JpaRepository<BankCalendarEntry, Long> {

    List<BankCalendarEntry> findByUser_Id(Long userId);

    List<BankCalendarEntry> findByUser_Email(String email);

    List<BankCalendarEntry> findByUser_IdAndType(Long userId, CalendarEntryType type);

    List<BankCalendarEntry> findByUser_IdAndExpectedDateBetween(Long userId, LocalDate from, LocalDate to);

    boolean existsBySourceContract_Id(Long contractId);

    boolean existsBySourceIncome_Id(Long incomeId);
}
