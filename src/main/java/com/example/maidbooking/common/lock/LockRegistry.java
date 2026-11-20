package com.example.maidbooking.common.lock;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Mutual exclusion over shared inventory (zone capacity, a maid's calendar, a job's state).
 * Implementations must reject acquiring locks out of zone, maid, job order, which rules out deadlock.
 * In-memory today; a Redis or database-backed implementation can replace it without touching callers.
 */
public interface LockRegistry {

    <T> T callWith(LockKey key, Supplier<T> action);

    /** Acquires every key in acquisition order, runs the action, then releases them all. */
    <T> T callWithAll(Collection<LockKey> keys, Supplier<T> action);

    default void runWith(LockKey key, Runnable action) {
        callWith(key, () -> {
            action.run();
            return null;
        });
    }
}
