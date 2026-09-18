package com.ramzi.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Expands a stored calendar seed + iCal-like RRULE onto a target month.
 * Supported: FREQ=MONTHLY|QUARTERLY|YEARLY, INTERVAL, BYMONTHDAY, BYMONTH.
 * Day 31 in shorter months is clamped to the last day of that month.
 */
public class RecurrenceCalculator {

    public Optional<LocalDate> occurrenceInMonth(LocalDate seedDate, String recurrenceRule, YearMonth month) {
        if (seedDate == null || month == null) {
            return Optional.empty();
        }
        return Recurrence.parse(recurrenceRule, seedDate).occurrenceIn(month);
    }

    public LocalDate clampToMonth(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(Math.max(dayOfMonth, 1), month.lengthOfMonth()));
    }

    private record Recurrence(Freq freq, int interval, int dayOfMonth, int monthOfYear, LocalDate seed) {

        Optional<LocalDate> occurrenceIn(YearMonth target) {
            YearMonth seedMonth = YearMonth.from(seed);
            return switch (freq) {
                case ONCE -> seedMonth.equals(target) ? Optional.of(seed) : Optional.empty();
                case YEARLY -> yearly(seedMonth, target);
                case MONTHLY, QUARTERLY -> monthly(seedMonth, target);
            };
        }

        private Optional<LocalDate> yearly(YearMonth seedMonth, YearMonth target) {
            if (target.getMonthValue() != monthOfYear) {
                return Optional.empty();
            }
            long years = ChronoUnit.YEARS.between(seedMonth, target);
            if (years < 0 || years % interval != 0) {
                return Optional.empty();
            }
            return Optional.of(clamp(target, dayOfMonth));
        }

        private Optional<LocalDate> monthly(YearMonth seedMonth, YearMonth target) {
            long months = ChronoUnit.MONTHS.between(seedMonth, target);
            if (months < 0 || months % interval != 0) {
                return Optional.empty();
            }
            return Optional.of(clamp(target, dayOfMonth));
        }

        static Recurrence parse(String rule, LocalDate seed) {
            Freq freq = Freq.ONCE;
            int interval = 1;
            int dayOfMonth = seed.getDayOfMonth();
            int monthOfYear = seed.getMonthValue();
            if (rule == null || rule.isBlank()) {
                return new Recurrence(freq, interval, dayOfMonth, monthOfYear, seed);
            }
            for (String part : rule.split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String key = pair[0].trim().toUpperCase(Locale.ROOT);
                String value = pair[1].trim().toUpperCase(Locale.ROOT);
                switch (key) {
                    case "FREQ" -> {
                        freq = Freq.from(value);
                        if (freq == Freq.QUARTERLY) {
                            interval = 3;
                            freq = Freq.MONTHLY;
                        }
                    }
                    case "INTERVAL" -> interval = Math.max(1, parseInt(value, 1));
                    case "BYMONTHDAY" -> dayOfMonth = clampInt(parseInt(value, dayOfMonth), 1, 31);
                    case "BYMONTH" -> monthOfYear = clampInt(parseInt(value, monthOfYear), 1, 12);
                    default -> {
                    }
                }
            }
            return new Recurrence(freq, interval, dayOfMonth, monthOfYear, seed);
        }

        private static LocalDate clamp(YearMonth month, int dayOfMonth) {
            return month.atDay(Math.min(Math.max(dayOfMonth, 1), month.lengthOfMonth()));
        }

        private static int parseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static int clampInt(int value, int min, int max) {
            return Math.min(max, Math.max(min, value));
        }
    }

    private enum Freq {
        ONCE,
        MONTHLY,
        QUARTERLY,
        YEARLY;

        static Freq from(String value) {
            return switch (value) {
                case "MONTHLY" -> MONTHLY;
                case "QUARTERLY" -> QUARTERLY;
                case "YEARLY" -> YEARLY;
                default -> ONCE;
            };
        }
    }
}
