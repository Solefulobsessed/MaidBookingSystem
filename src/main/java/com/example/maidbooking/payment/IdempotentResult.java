package com.example.maidbooking.payment;

/** The outcome of an idempotent call; {@code replayed} is true when it came from an earlier request. */
public record IdempotentResult<T>(T value, boolean replayed) {
}
