package com.example.maidbooking.booking.type;

/** When a booking type gets its maid. */
public enum AllocationMode {
    /** Reserve capacity and assign a maid in the same request (instant). */
    DISPATCH_NOW,
    /** Reserve capacity now; the maid is allocated once the job is paid (scheduled, recurring). */
    ALLOCATE_AFTER_PAYMENT
}
