package com.example.maidbooking.booking.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class ScheduledBookingHandlerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

    private final ScheduledBookingHandler handler = new ScheduledBookingHandler(120, 30);

    static CreateBookingRequest scheduled(LocalDateTime start, Integer minutes) {
        return new CreateBookingRequest(BookingType.SCHEDULED, "c-1", "koramangala", "Flat 302",
                ServiceType.CLEANING, null, start, minutes, null, null);
    }

    @Test
    void acceptsTheEdgesOfTheWindow() {
        handler.validate(scheduled(NOW.plusHours(2), 60), NOW);
        handler.validate(scheduled(NOW.plusDays(30), 60), NOW);
    }

    @Test
    void rejectsLessThanTwoHoursAhead() {
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.plusMinutes(90), 60), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at least 120 minutes");
    }

    @Test
    void rejectsMoreThanThirtyDaysAhead() {
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.plusDays(30).plusMinutes(30), 60), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("within 30 days");
    }

    @Test
    void rejectsAStartInThePast() {
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.minusDays(1), 60), NOW))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 45})
    void rejectsAStartOffTheHalfHour(int minute) {
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.plusDays(1).withMinute(minute), 60), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("hour or half hour");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 45, 270})
    void rejectsABadDuration(int minutes) {
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.plusDays(1), minutes), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Duration");
    }

    @Test
    void rejectsAMissingStartOrDuration() {
        assertThatThrownBy(() -> handler.validate(scheduled(null, 60), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a start");
        assertThatThrownBy(() -> handler.validate(scheduled(NOW.plusDays(1), null), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a start");
    }

    @Test
    void aJobMayEndAtMidnightButNotRunPastIt() {
        LocalDateTime lateEvening = LocalDateTime.of(2026, 11, 21, 23, 0);
        handler.validate(scheduled(lateEvening, 60), NOW);
        assertThatThrownBy(() -> handler.validate(scheduled(lateEvening, 90), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("midnight");
    }

    @Test
    void plansOneJobAtTheChosenStart() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 23, 9, 0);

        BookingPlan plan = handler.plan(scheduled(start, 120), NOW);

        assertThat(plan.jobSlots()).containsExactly(new TimeSlot(start, start.plusHours(2)));
        assertThat(handler.type()).isEqualTo(BookingType.SCHEDULED);
        assertThat(handler.allocationMode()).isEqualTo(AllocationMode.ALLOCATE_AFTER_PAYMENT);
    }

    @Test
    void requestDefaultsToStandardTierAndNoPreferences() {
        CreateBookingRequest request = scheduled(NOW.plusDays(1), 60);

        assertThat(request.tier()).isEqualTo(Tier.STANDARD);
        assertThat(request.preferences()).isEmpty();
    }
}
