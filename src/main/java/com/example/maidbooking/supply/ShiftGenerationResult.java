package com.example.maidbooking.supply;

import java.util.List;

/** Shifts created from a template, and how many windows were skipped (clashing shift, or leave that day). */
public record ShiftGenerationResult(List<Shift> created, int skippedOverlapping, int skippedForLeave) {
}
