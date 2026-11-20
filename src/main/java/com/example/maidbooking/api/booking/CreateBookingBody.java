package com.example.maidbooking.api.booking;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/** JSON body of {@code POST /bookings}; type-specific fields are checked by the booking type's handler. */
public record CreateBookingBody(@NotNull BookingType type, @NotBlank String customerId, @NotBlank String locality,
                                @NotBlank String address, @NotNull ServiceType serviceType, Tier tier,
                                LocalDateTime start, Integer durationMinutes, Map<String, String> preferences,
                                String preferredMaidId, @Valid RecurrenceBody recurrence) {

    /** "Every MONDAY..FRIDAY at 09:00 for 60 minutes, from startDate to endDate (inclusive)". */
    public record RecurrenceBody(@NotEmpty Set<DayOfWeek> daysOfWeek, @NotNull LocalTime startTime,
                                 @NotNull Integer durationMinutes, @NotNull LocalDate startDate,
                                 @NotNull LocalDate endDate) {

        RecurrenceRule toRule() {
            return new RecurrenceRule(daysOfWeek, startTime, Duration.ofMinutes(durationMinutes), startDate, endDate);
        }
    }

    CreateBookingRequest toRequest() {
        return new CreateBookingRequest(type, customerId, locality, address, serviceType, tier, start,
                durationMinutes, preferences, preferredMaidId, recurrence == null ? null : recurrence.toRule());
    }
}
