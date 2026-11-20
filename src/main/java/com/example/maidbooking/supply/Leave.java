package com.example.maidbooking.supply;

import java.time.LocalDate;
import java.util.Objects;

import com.example.maidbooking.common.error.ValidationException;

/** Whole days a maid is unavailable, both dates inclusive. */
public record Leave(String id, String maidId, LocalDate from, LocalDate to, String reason) {

    public Leave {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(maidId, "maidId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new ValidationException("Leave end " + to + " is before start " + from);
        }
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
