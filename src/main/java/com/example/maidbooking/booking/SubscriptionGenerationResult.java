package com.example.maidbooking.booking;

/** What one generation run did: visits created, and visits skipped for lack of capacity. */
public record SubscriptionGenerationResult(int created, int skippedNoCapacity) {
}
