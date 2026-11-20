package com.example.maidbooking.booking;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** The life of one visit. COMPLETED, CANCELLED, EXPIRED and UNFULFILLED are final. */
public enum JobStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    ASSIGNED,
    EN_ROUTE,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    UNFULFILLED;

    private static final Map<JobStatus, Set<JobStatus>> NEXT = new EnumMap<>(Map.of(
            PENDING_PAYMENT, Set.of(CONFIRMED, EXPIRED, CANCELLED),
            CONFIRMED, Set.of(ASSIGNED, UNFULFILLED, CANCELLED),
            ASSIGNED, Set.of(CONFIRMED, EN_ROUTE, CANCELLED),
            EN_ROUTE, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(COMPLETED)));

    public boolean canMoveTo(JobStatus target) {
        return NEXT.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isFinal() {
        return !NEXT.containsKey(this);
    }

    /** Statuses in which the customer may still cancel (before the maid sets off). */
    public boolean isCancellable() {
        return canMoveTo(CANCELLED);
    }
}
