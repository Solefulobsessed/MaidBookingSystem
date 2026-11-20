package com.example.maidbooking.common;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.example.maidbooking.common.error.ValidationException;

/**
 * A repeating slot, e.g. "weekdays 09:00 for 60 minutes from 23 Nov to 28 Feb".
 * Both dates are inclusive. Each occurrence stays within one day.
 */
public record RecurrenceRule(Set<DayOfWeek> daysOfWeek, LocalTime startTime, Duration duration,
                             LocalDate startDate, LocalDate endDate) {

    public RecurrenceRule {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            throw new ValidationException("Recurrence needs at least one day of the week");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new ValidationException("Recurrence duration must be positive");
        }
        if (Duration.between(startTime, LocalTime.MAX).compareTo(duration) < 0) {
            throw new ValidationException("Recurring slot must end on the same day it starts");
        }
        if (endDate.isBefore(startDate)) {
            throw new ValidationException("Recurrence end date " + endDate + " is before start date " + startDate);
        }
        daysOfWeek = Set.copyOf(daysOfWeek);
    }

    /** Occurrences on dates in {@code [from, to]} that also fall inside the rule's own date range, in order. */
    public List<TimeSlot> expand(LocalDate from, LocalDate to) {
        LocalDate first = from.isAfter(startDate) ? from : startDate;
        LocalDate last = to.isBefore(endDate) ? to : endDate;
        List<TimeSlot> slots = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (daysOfWeek.contains(date.getDayOfWeek())) {
                slots.add(TimeSlot.of(date.atTime(startTime), duration));
            }
        }
        return slots;
    }

    public List<TimeSlot> expandAll() {
        return expand(startDate, endDate);
    }
}
