package com.example.maidbooking.capacity;

import java.util.Objects;

/**
 * Minutes of maid time a bucket can sell ({@code capacity}) and minutes already promised
 * ({@code reserved}). Mutated only under the zone lock; {@code version} counts changes.
 */
public class CapacityBucket {

    private final BucketKey key;
    private long capacityMinutes;
    private long reservedMinutes;
    private long version;

    public CapacityBucket(BucketKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    /** Sellable minutes left: capacity scaled down by the utilisation factor, minus what is reserved. */
    public long free(double utilisationFactor) {
        return (long) Math.floor(capacityMinutes * utilisationFactor) - reservedMinutes;
    }

    public void addCapacity(long minutes) {
        capacityMinutes += requirePositive(minutes);
        version++;
    }

    /** Capacity may drop below what is already reserved (e.g. leave); the bucket then shows as full. */
    public void removeCapacity(long minutes) {
        capacityMinutes = Math.max(0, capacityMinutes - requirePositive(minutes));
        version++;
    }

    public void reserve(long minutes, double utilisationFactor) {
        if (free(utilisationFactor) < requirePositive(minutes)) {
            throw new IllegalStateException("Bucket " + key + " has only " + free(utilisationFactor) + " free minutes");
        }
        reservedMinutes += minutes;
        version++;
    }

    public void release(long minutes) {
        if (requirePositive(minutes) > reservedMinutes) {
            throw new IllegalStateException("Releasing " + minutes + " min but only " + reservedMinutes + " reserved in " + key);
        }
        reservedMinutes -= minutes;
        version++;
    }

    private static long requirePositive(long minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes must be positive: " + minutes);
        }
        return minutes;
    }

    public BucketKey key() { return key; }
    public long capacityMinutes() { return capacityMinutes; }
    public long reservedMinutes() { return reservedMinutes; }
    public long version() { return version; }
}
