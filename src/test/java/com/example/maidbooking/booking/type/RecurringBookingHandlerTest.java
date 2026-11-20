package com.example.maidbooking.booking.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.booking.Subscription;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

class RecurringBookingHandlerTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime NOW = MON.atTime(8, 0);
    private static final Set<DayOfWeek> WEEKDAYS = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final RecurringBookingHandler handler;

    RecurringBookingHandlerTest() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        handler = new RecurringBookingHandler(new LocalityZoneResolver(zones), 120, 30);
    }

    private static CreateBookingRequest recurring(RecurrenceRule rule) {
        return new CreateBookingRequest(BookingType.RECURRING, "c-1", "koramangala", "Flat 302", ServiceType.CLEANING,
                Tier.PREMIUM, null, null, null, "m-7f3a", rule);
    }

    private static RecurrenceRule rule(LocalTime at, int minutes, LocalDate from, LocalDate to) {
        return new RecurrenceRule(WEEKDAYS, at, Duration.ofMinutes(minutes), from, to);
    }

    @Test
    void plansTheWeekdaysOfTheFirstFourteenDaysAndTheSubscription() {
        RecurrenceRule rule = rule(LocalTime.of(11, 0), 60, MON, MON.plusMonths(3));

        BookingPlan plan = handler.plan(recurring(rule), NOW);

        assertThat(plan.jobSlots()).hasSize(10)
                .startsWith(TimeSlot.of(MON.atTime(11, 0), Duration.ofHours(1)))
                .endsWith(TimeSlot.of(MON.plusDays(11).atTime(11, 0), Duration.ofHours(1)));
        Subscription subscription = plan.subscription();
        assertThat(subscription.zoneId()).isEqualTo("kor-5");
        assertThat(subscription.rule()).isEqualTo(rule);
        assertThat(subscription.tier()).isEqualTo(Tier.PREMIUM);
        assertThat(subscription.preferredMaidId()).isEqualTo("m-7f3a");
        assertThat(subscription.generatedUntil()).isEqualTo(MON.plusDays(13));
        assertThat(handler.allocationMode()).isEqualTo(AllocationMode.ALLOCATE_AFTER_PAYMENT);
    }

    @Test
    void skipsTodaysVisitWhenItIsLessThanTwoHoursAway() {
        BookingPlan plan = handler.plan(recurring(rule(LocalTime.of(9, 0), 60, MON, MON.plusMonths(1))), NOW);

        assertThat(plan.jobSlots()).hasSize(9).first().satisfies(slot ->
                assertThat(slot.start()).isEqualTo(MON.plusDays(1).atTime(9, 0)));
    }

    @Test
    void aShortSeriesIsGeneratedOnlyUpToItsEnd() {
        BookingPlan plan = handler.plan(recurring(rule(LocalTime.of(11, 0), 60, MON, MON.plusDays(8))), NOW);

        assertThat(plan.jobSlots()).hasSize(7);
        assertThat(plan.subscription().generatedUntil()).isEqualTo(MON.plusDays(8));
    }

    @Test
    void acceptsOneWeekToSixMonths() {
        handler.validate(recurring(rule(LocalTime.of(9, 0), 60, MON, MON.plusDays(6))), NOW);
        handler.validate(recurring(rule(LocalTime.of(9, 0), 60, MON, MON.plusMonths(6))), NOW);

        assertThatThrownBy(() -> handler.validate(recurring(rule(LocalTime.of(9, 0), 60, MON, MON.plusDays(5))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at least 1 week");
        assertThatThrownBy(() -> handler.validate(
                recurring(rule(LocalTime.of(9, 0), 60, MON, MON.plusMonths(6).plusDays(1))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at most 6 months");
    }

    @Test
    void startsBetweenTodayAndThirtyDaysAhead() {
        assertThatThrownBy(() -> handler.validate(
                recurring(rule(LocalTime.of(9, 0), 60, MON.minusDays(1), MON.plusMonths(1))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("between today");
        assertThatThrownBy(() -> handler.validate(
                recurring(rule(LocalTime.of(9, 0), 60, MON.plusDays(31), MON.plusMonths(2))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("30 days");
    }

    @Test
    void visitsFollowTheDurationAndHalfHourRules() {
        assertThatThrownBy(() -> handler.validate(recurring(rule(LocalTime.of(9, 0), 45, MON, MON.plusMonths(1))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Duration");
        assertThatThrownBy(() -> handler.validate(recurring(rule(LocalTime.of(9, 15), 60, MON, MON.plusMonths(1))), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("half hour");
        assertThatThrownBy(() -> handler.validate(recurring(null), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a recurrence");
    }
}
