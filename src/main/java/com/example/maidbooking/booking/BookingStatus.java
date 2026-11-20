package com.example.maidbooking.booking;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** A booking's status is never stored: it is worked out from its jobs. */
public enum BookingStatus {
    PENDING_PAYMENT,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    private static final Set<JobStatus> PAID = EnumSet.of(JobStatus.CONFIRMED, JobStatus.ASSIGNED,
            JobStatus.EN_ROUTE, JobStatus.IN_PROGRESS, JobStatus.COMPLETED);

    /**
     * Some job still open: ACTIVE once anything has been paid, else PENDING_PAYMENT.
     * All jobs final: COMPLETED if any was done, else CANCELLED (cancelled, expired or unfulfilled).
     */
    public static BookingStatus of(Collection<JobStatus> jobs) {
        boolean anyOpen = jobs.stream().anyMatch(status -> !status.isFinal());
        boolean anyPaid = jobs.stream().anyMatch(PAID::contains);
        if (anyOpen) {
            return anyPaid ? ACTIVE : PENDING_PAYMENT;
        }
        return jobs.contains(JobStatus.COMPLETED) ? COMPLETED : CANCELLED;
    }
}
