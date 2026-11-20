package com.example.maidbooking.payment;

/** One run of the recurring charge job: visits paid, and visits cancelled because they couldn't be. */
public record RecurringChargeResult(int charged, int cancelled) {
}
