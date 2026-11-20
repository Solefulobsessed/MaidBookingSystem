package com.example.maidbooking.supply;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

import com.example.maidbooking.common.error.ValidationException;

/** One weekly working window, e.g. "Monday, Koramangala, 07:00-13:00". */
public record ShiftTemplate(DayOfWeek day, String zoneId, LocalTime start, LocalTime end) {

    public ShiftTemplate {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Duration length = Duration.between(start, end);
        if (length.compareTo(Shift.MIN_LENGTH) < 0 || length.compareTo(Shift.MAX_LENGTH) > 0) {
            throw new ValidationException("Template window on " + day + " must be 2 to 10 hours within the day");
        }
    }

    boolean overlaps(ShiftTemplate other) {
        return day == other.day && start.isBefore(other.end) && other.start.isBefore(end);
    }
}
