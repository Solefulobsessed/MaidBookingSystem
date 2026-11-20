package com.example.maidbooking.common;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ValidationException;

class RecurrenceRuleTest {

    // Mon 23 Nov 2026 .. Sun 6 Dec 2026 = two full weeks
    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDate SUN_WEEK2 = LocalDate.of(2026, 12, 6);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);

    private static RecurrenceRule rule(Set<DayOfWeek> days, String time, int minutes, LocalDate from, LocalDate to) {
        return new RecurrenceRule(days, LocalTime.parse(time), Duration.ofMinutes(minutes), from, to);
    }

    @Test
    void weekdaysAtNineGiveTenSlotsInTwoWeeks() {
        List<TimeSlot> slots = rule(WEEKDAYS, "09:00", 60, MON, SUN_WEEK2).expandAll();

        assertThat(slots).hasSize(10);
        assertThat(slots.get(0)).isEqualTo(TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1)));
        assertThat(slots).extracting(s -> s.start().getDayOfWeek()).doesNotContain(DayOfWeek.SATURDAY, SUNDAY);
        assertThat(slots).isSortedAccordingTo((a, b) -> a.start().compareTo(b.start()));
    }

    @Test
    void sundaysOnly() {
        List<TimeSlot> slots = rule(Set.of(SUNDAY), "10:30", 120, MON, SUN_WEEK2).expandAll();

        assertThat(slots).extracting(TimeSlot::start)
                .containsExactly(LocalDate.of(2026, 11, 29).atTime(10, 30), SUN_WEEK2.atTime(10, 30));
    }

    @Test
    void endDateIsInclusive() {
        assertThat(rule(Set.of(FRIDAY), "09:00", 60, MON, LocalDate.of(2026, 11, 27)).expandAll()).hasSize(1);
    }

    @Test
    void expandWindowIsClippedToTheRuleRange() {
        RecurrenceRule rule = rule(WEEKDAYS, "09:00", 60, MON, SUN_WEEK2);

        assertThat(rule.expand(MON.minusDays(10), MON)).hasSize(1);
        assertThat(rule.expand(MON.plusDays(7), MON.plusDays(8))).hasSize(2);
        assertThat(rule.expand(SUN_WEEK2.plusDays(1), SUN_WEEK2.plusDays(30))).isEmpty();
    }

    @Test
    void daysAreCopiedSoCallersCannotChangeTheRule() {
        Set<DayOfWeek> days = new HashSet<>(Set.of(MONDAY));
        RecurrenceRule rule = rule(days, "09:00", 60, MON, SUN_WEEK2);
        days.add(TUESDAY);

        assertThat(rule.daysOfWeek()).containsExactly(MONDAY);
    }

    @Test
    void rejectsInvalidRules() {
        assertThatThrownBy(() -> rule(Set.of(), "09:00", 60, MON, SUN_WEEK2)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> rule(WEEKDAYS, "09:00", 0, MON, SUN_WEEK2)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> rule(WEEKDAYS, "09:00", 60, SUN_WEEK2, MON)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> rule(WEEKDAYS, "23:30", 60, MON, SUN_WEEK2))
                .isInstanceOf(ValidationException.class).hasMessageContaining("same day");
    }

    @Test
    void lateSlotThatFitsBeforeMidnightIsAllowed() {
        assertThat(rule(Set.of(MONDAY), "22:30", 60, MON, MON).expandAll()).hasSize(1);
    }
}
