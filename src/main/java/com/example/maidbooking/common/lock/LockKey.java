package com.example.maidbooking.common.lock;

import java.util.Comparator;
import java.util.Objects;

/** Identifies one lock. Keys sort in acquisition order: by level, then by id. */
public record LockKey(LockLevel level, String id) implements Comparable<LockKey> {

    private static final Comparator<LockKey> ORDER =
            Comparator.comparing(LockKey::level).thenComparing(LockKey::id);

    public LockKey {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(id, "id");
    }

    public static LockKey zone(String zoneId) {
        return new LockKey(LockLevel.ZONE, zoneId);
    }

    public static LockKey maid(String maidId) {
        return new LockKey(LockLevel.MAID, maidId);
    }

    public static LockKey job(String jobId) {
        return new LockKey(LockLevel.JOB, jobId);
    }

    @Override
    public int compareTo(LockKey other) {
        return ORDER.compare(this, other);
    }
}
