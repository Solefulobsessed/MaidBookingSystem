package com.example.maidbooking.booking;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/**
 * What a customer asks for when booking. Fields a booking type does not use stay null (an instant
 * booking has no {@code start}, only a recurring one has a {@code recurrence}); each {@link com.example.maidbooking.booking.type.BookingTypeHandler}
 * checks the fields it needs.
 */
public record CreateBookingRequest(
        BookingType type,
        String customerId,
        String locality,
        String address,
        ServiceType service,
        Tier tier,
        LocalDateTime start,
        Integer durationMinutes,
        Map<String, String> preferences,
        String preferredMaidId,
        RecurrenceRule recurrence) {

    public CreateBookingRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(locality, "locality");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(service, "service");
        tier = tier == null ? Tier.STANDARD : tier;
        preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
    }
}
