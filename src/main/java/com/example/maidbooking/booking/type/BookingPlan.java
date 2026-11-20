package com.example.maidbooking.booking.type;

import java.util.List;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;

/** The jobs a booking will create, one slot per job, in time order. */
public record BookingPlan(List<TimeSlot> jobSlots) {

    public BookingPlan {
        if (jobSlots == null || jobSlots.isEmpty()) {
            throw new ValidationException("A booking must plan at least one job");
        }
        jobSlots = List.copyOf(jobSlots);
    }

    public static BookingPlan single(TimeSlot slot) {
        return new BookingPlan(List.of(slot));
    }
}
