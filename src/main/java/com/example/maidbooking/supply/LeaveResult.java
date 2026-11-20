package com.example.maidbooking.supply;

import java.util.List;

/** The leave recorded and the planned shifts it cancelled. */
public record LeaveResult(Leave leave, List<Shift> cancelledShifts) {
}
