package com.example.maidbooking.supply;

import java.util.List;
import java.util.Objects;

import com.example.maidbooking.common.error.ValidationException;

/** A maid's preferred weekly pattern. It only generates shifts; it is never availability itself. */
public record WeeklyShiftTemplate(String maidId, List<ShiftTemplate> windows) {

    public WeeklyShiftTemplate {
        Objects.requireNonNull(maidId, "maidId");
        if (windows == null || windows.isEmpty()) {
            throw new ValidationException("A shift template needs at least one window");
        }
        windows = List.copyOf(windows);
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                if (windows.get(i).overlaps(windows.get(j))) {
                    throw new ValidationException("Template windows overlap on " + windows.get(i).day());
                }
            }
        }
    }
}
