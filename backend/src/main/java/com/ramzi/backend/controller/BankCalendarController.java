package com.ramzi.backend.controller;

import com.ramzi.backend.dto.AvailableBalanceDto;
import com.ramzi.backend.dto.CalendarMonthViewDto;
import com.ramzi.backend.dto.CalendarMonthViewDto.HistoricalDayDto;
import com.ramzi.backend.dto.CalendarOccurrenceDto;
import com.ramzi.backend.service.BankCalendarService;
import com.ramzi.backend.service.BankConnectionOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequiredArgsConstructor
public class BankCalendarController {

    private final BankCalendarService calendarService;
    private final BankConnectionOrchestrationService bankConnections;
    private final Clock clock;

    @GetMapping({"/api/calendar", "/calendar"})
    public ResponseEntity<List<CalendarOccurrenceDto>> getCalendar(
            @RequestParam(required = false) String month,
            Principal principal) {
        return ResponseEntity.ok(calendarService.getCalendar(principal.getName(), parseMonth(month)));
    }

    @GetMapping({"/api/calendar/available-balance", "/calendar/available-balance"})
    public ResponseEntity<AvailableBalanceDto> availableBalance(Principal principal) {
        return ResponseEntity.ok(calendarService.availableBalance(principal.getName()));
    }

    @GetMapping({"/api/calendar/month-view", "/calendar/month-view"})
    public ResponseEntity<CalendarMonthViewDto> getMonthView(
            @RequestParam(required = false) String month,
            Principal principal) {
        bankConnections.importTransactionsIfMissing(principal.getName());
        return ResponseEntity.ok(calendarService.getMonthView(principal.getName(), parseMonth(month)));
    }

    @GetMapping({"/api/calendar/day-view", "/calendar/day-view"})
    public ResponseEntity<HistoricalDayDto> getDayView(
            @RequestParam String date,
            Principal principal) {
        try {
            bankConnections.importTransactionsIfMissing(principal.getName());
            return ResponseEntity.ok(calendarService.getDayView(principal.getName(), parseDate(date)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be YYYY-MM-DD");
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be YYYY-MM");
        }
    }
}
