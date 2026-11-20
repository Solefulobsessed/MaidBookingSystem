package com.example.maidbooking.supply;

import com.example.maidbooking.common.TimeSlot;

/**
 * Published whenever a shift starts or stops counting towards capacity, so the capacity ledger
 * can add or remove the maid's minutes without the supply package knowing about capacity.
 */
public record ShiftChanged(String shiftId, String maidId, String zoneId, TimeSlot window, Change change) {

    public enum Change { CAPACITY_ADDED, CAPACITY_REMOVED }

    public static ShiftChanged added(Shift shift) {
        return new ShiftChanged(shift.id(), shift.maidId(), shift.zoneId(), shift.window(), Change.CAPACITY_ADDED);
    }

    public static ShiftChanged removed(Shift shift) {
        return new ShiftChanged(shift.id(), shift.maidId(), shift.zoneId(), shift.window(), Change.CAPACITY_REMOVED);
    }
}
