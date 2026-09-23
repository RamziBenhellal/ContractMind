package com.ramzi.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceCalculatorTest {

    private final RecurrenceCalculator calculator = new RecurrenceCalculator();

    @Test
    void monthlyRuleHitsTheSameDayEachMonth() {
        LocalDate seed = LocalDate.of(2026, 1, 15);

        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=15", "2026-01"))
                .contains(LocalDate.of(2026, 1, 15));
        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=15", "2026-02"))
                .contains(LocalDate.of(2026, 2, 15));
        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=15", "2026-03"))
                .contains(LocalDate.of(2026, 3, 15));
    }

    @Test
    void day31InFebruaryIsClampedToLastDay() {
        LocalDate seed = LocalDate.of(2026, 1, 31);

        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=31", "2026-02"))
                .contains(LocalDate.of(2026, 2, 28));
        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=31", "2026-03"))
                .contains(LocalDate.of(2026, 3, 31));
        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=31", "2026-04"))
                .contains(LocalDate.of(2026, 4, 30));
    }

    @Test
    void day31InLeapFebruaryBecomes29() {
        LocalDate seed = LocalDate.of(2028, 1, 31);

        assertThat(occurrence(seed, "FREQ=MONTHLY;BYMONTHDAY=31", "2028-02"))
                .contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void yearlyContractOnlyAppearsInTheSeedMonth() {
        LocalDate seed = LocalDate.of(2025, 3, 15);
        String rule = "FREQ=YEARLY;BYMONTHDAY=15";

        assertThat(occurrence(seed, rule, "2025-03")).contains(LocalDate.of(2025, 3, 15));
        assertThat(occurrence(seed, rule, "2026-03")).contains(LocalDate.of(2026, 3, 15));
        assertThat(occurrence(seed, rule, "2026-04")).isEmpty();
        assertThat(occurrence(seed, rule, "2026-02")).isEmpty();
        assertThat(occurrence(seed, rule, "2024-03")).isEmpty();
    }

    @Test
    void yearlyFebruary29FallsBackTo28InCommonYears() {
        LocalDate seed = LocalDate.of(2024, 2, 29);
        String rule = "FREQ=YEARLY;BYMONTHDAY=29";

        assertThat(occurrence(seed, rule, "2024-02")).contains(LocalDate.of(2024, 2, 29));
        assertThat(occurrence(seed, rule, "2025-02")).contains(LocalDate.of(2025, 2, 28));
        assertThat(occurrence(seed, rule, "2028-02")).contains(LocalDate.of(2028, 2, 29));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01,2026-01-31",
            "2026-04,2026-04-30",
            "2026-07,2026-07-31",
            "2026-10,2026-10-31",
            "2027-01,2027-01-31"
    })
    void quarterlyFromJanuary31LandsOnQuarterMonths(String month, String expected) {
        LocalDate seed = LocalDate.of(2026, 1, 31);

        assertThat(occurrence(seed, "FREQ=QUARTERLY;BYMONTHDAY=31", month))
                .contains(LocalDate.parse(expected));
    }

    @Test
    void quarterlySkipsOffQuarterMonths() {
        LocalDate seed = LocalDate.of(2026, 1, 31);

        assertThat(occurrence(seed, "FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=31", "2026-02")).isEmpty();
        assertThat(occurrence(seed, "FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=31", "2026-03")).isEmpty();
        assertThat(occurrence(seed, "FREQ=QUARTERLY", "2026-05")).isEmpty();
    }

    @Test
    void everySecondMonthStartsFromTheSeed() {
        LocalDate seed = LocalDate.of(2026, 1, 10);
        String rule = "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=10";

        assertThat(occurrence(seed, rule, "2026-01")).contains(LocalDate.of(2026, 1, 10));
        assertThat(occurrence(seed, rule, "2026-02")).isEmpty();
        assertThat(occurrence(seed, rule, "2026-03")).contains(LocalDate.of(2026, 3, 10));
    }

    @Test
    void oneShotEntryOnlyAppearsInItsOwnMonth() {
        LocalDate seed = LocalDate.of(2026, 9, 18);

        assertThat(occurrence(seed, null, "2026-09")).contains(seed);
        assertThat(occurrence(seed, "", "2026-09")).contains(seed);
        assertThat(occurrence(seed, null, "2026-10")).isEmpty();
    }

    @Test
    void missingRuleUsesSeedDayWhenFreqIsMonthly() {
        LocalDate seed = LocalDate.of(2026, 5, 7);

        assertThat(occurrence(seed, "FREQ=MONTHLY", "2026-06"))
                .contains(LocalDate.of(2026, 6, 7));
    }

    @Test
    void clampToMonthUtilityHonorsShortMonths() {
        assertThat(calculator.clampToMonth(YearMonth.of(2026, 2), 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(calculator.clampToMonth(YearMonth.of(2028, 2), 31))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    private Optional<LocalDate> occurrence(LocalDate seed, String rule, String month) {
        return calculator.occurrenceInMonth(seed, rule, YearMonth.parse(month));
    }
}
