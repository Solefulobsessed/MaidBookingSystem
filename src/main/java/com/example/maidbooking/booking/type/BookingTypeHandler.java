package com.example.maidbooking.booking.type;

import java.time.LocalDateTime;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;

/** Everything that differs between booking types. Add a type by adding one handler bean. */
public interface BookingTypeHandler {

    BookingType type();

    /** Rejects a request this type can't accept, with a {@code ValidationException}. */
    void validate(CreateBookingRequest request, LocalDateTime now);

    /** The job slots to create. Called only after {@link #validate} passed. */
    BookingPlan plan(CreateBookingRequest request, LocalDateTime now);

    AllocationMode allocationMode();
}
