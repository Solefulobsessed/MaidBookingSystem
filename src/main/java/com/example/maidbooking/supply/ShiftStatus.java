package com.example.maidbooking.supply;

/** A shift adds to zone capacity only while PLANNED or CHECKED_IN. */
public enum ShiftStatus {
    PLANNED,
    CHECKED_IN,
    ENDED,
    ABSENT,
    CANCELLED;

    public boolean addsCapacity() {
        return this == PLANNED || this == CHECKED_IN;
    }
}
