package com.example.maidbooking.supply;

import java.util.List;

/** Shifts created from a template, and how many windows were skipped because a shift was already there. */
public record ShiftGenerationResult(List<Shift> created, int skippedOverlapping) {
}
