package com.example.maidbooking.capacity;

/** Published after an unpaid hold lapses and its minutes are given back, so the booking side can expire the job. */
public record CapacityHoldExpired(String reservationId, String referenceId, String zoneId) {
}
