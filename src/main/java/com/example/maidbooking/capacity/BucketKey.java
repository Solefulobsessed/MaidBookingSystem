package com.example.maidbooking.capacity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;

/**
 * One 30-minute bucket of one capacity pool in one zone. Each service has its own pool, and the
 * {@value #ZONE_POOL} pool counts every maid once, so a maid with two skills is never sold twice.
 */
public record BucketKey(String zoneId, String pool, LocalDateTime start) {

    public static final int BUCKET_MINUTES = 30;
    public static final String ZONE_POOL = "ANY";

    public BucketKey {
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(start, "start");
        if (start.getMinute() % BUCKET_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw new ValidationException("Bucket must start on a " + BUCKET_MINUTES + "-minute boundary: " + start);
        }
    }

    public static BucketKey forService(String zoneId, ServiceType service, LocalDateTime start) {
        return new BucketKey(zoneId, service.name(), start);
    }

    public static BucketKey forZone(String zoneId, LocalDateTime start) {
        return new BucketKey(zoneId, ZONE_POOL, start);
    }
}
