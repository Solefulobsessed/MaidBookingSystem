package com.example.maidbooking.booking.type;

import java.util.List;

import com.example.maidbooking.booking.Subscription;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;

/**
 * The jobs a booking will create, one slot per job, in time order, and for a recurring booking the subscription
 * that keeps generating them ({@code null} otherwise).
 */
public record BookingPlan(List<TimeSlot> jobSlots, Subscription subscription) {

    public BookingPlan {
        if (jobSlots == null || jobSlots.isEmpty()) {
            throw new ValidationException("A booking must plan at least one job");
        }
        jobSlots = List.copyOf(jobSlots);
    }

    public BookingPlan(List<TimeSlot> jobSlots) {
        this(jobSlots, null);
    }

    public static BookingPlan single(TimeSlot slot) {
        return new BookingPlan(List.of(slot));
    }
}
