package com.example.maidbooking.api.booking;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/** JSON body of {@code POST /bookings}; type-specific fields are checked by the booking type's handler. */
public record CreateBookingBody(@NotNull BookingType type, @NotBlank String customerId, @NotBlank String locality,
                                @NotBlank String address, @NotNull ServiceType serviceType, Tier tier,
                                LocalDateTime start, Integer durationMinutes, Map<String, String> preferences,
                                String preferredMaidId) {

    CreateBookingRequest toRequest() {
        return new CreateBookingRequest(type, customerId, locality, address, serviceType, tier, start,
                durationMinutes, preferences, preferredMaidId);
    }
}
