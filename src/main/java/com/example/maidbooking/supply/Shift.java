package com.example.maidbooking.supply;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

/** A maid working in one zone for one stretch of one day: the unit of supply. */
public class Shift {

    static final Duration MIN_LENGTH = Duration.ofHours(2);
    static final Duration MAX_LENGTH = Duration.ofHours(10);

    private final String id;
    private final String maidId;
    private final String zoneId;
    private final TimeSlot window;
    private ShiftStatus status;

    public Shift(String id, String maidId, String zoneId, TimeSlot window) {
        this.id = Objects.requireNonNull(id, "id");
        this.maidId = Objects.requireNonNull(maidId, "maidId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.window = Objects.requireNonNull(window, "window");
        if (!window.end().toLocalDate().equals(window.start().toLocalDate())) {
            throw new ValidationException("A shift must start and end on the same day");
        }
        Duration length = window.duration();
        if (length.compareTo(MIN_LENGTH) < 0 || length.compareTo(MAX_LENGTH) > 0) {
            throw new ValidationException("A shift must last between 2 and 10 hours, was " + length.toMinutes() + " min");
        }
        this.status = ShiftStatus.PLANNED;
    }

    public void checkIn() {
        move(ShiftStatus.PLANNED, ShiftStatus.CHECKED_IN, "check in");
    }

    public void end() {
        move(ShiftStatus.CHECKED_IN, ShiftStatus.ENDED, "end");
    }

    public void markAbsent() {
        move(ShiftStatus.PLANNED, ShiftStatus.ABSENT, "mark absent");
    }

    public void cancel() {
        move(ShiftStatus.PLANNED, ShiftStatus.CANCELLED, "cancel");
    }

    private void move(ShiftStatus from, ShiftStatus to, String action) {
        if (status != from) {
            throw new InvalidStateTransitionException("Shift", status, action);
        }
        status = to;
    }

    public boolean addsCapacity() {
        return status.addsCapacity();
    }

    public LocalDate date() {
        return window.start().toLocalDate();
    }

    public String id() { return id; }
    public String maidId() { return maidId; }
    public String zoneId() { return zoneId; }
    public TimeSlot window() { return window; }
    public ShiftStatus status() { return status; }
}
