package com.example.maidbooking.capacity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.lock.LockKey;
import com.example.maidbooking.common.lock.LockRegistry;
import com.example.maidbooking.supply.ServiceType;

/**
 * The only way to promise zone capacity. A reservation must fit both its service pool and the
 * zone-wide pool in every bucket it touches, or nothing is reserved.
 */
@Service
public class CapacityService {

    private final CapacityBucketRepository buckets;
    private final CapacityReservationRepository reservations;
    private final LockRegistry locks;
    private final double utilisationFactor;

    public CapacityService(CapacityBucketRepository buckets, CapacityReservationRepository reservations,
                           LockRegistry locks, CapacityProperties properties) {
        this.buckets = buckets;
        this.reservations = reservations;
        this.locks = locks;
        this.utilisationFactor = properties.utilisationFactor();
    }

    /**
     * @param blocked     the job's time including its travel buffer
     * @param holdUntil   when an unpaid hold lapses; {@code null} for no expiry
     * @throws ConflictException SLOT_UNAVAILABLE listing every bucket that lacks room
     */
    public CapacityReservation reserve(String zoneId, ServiceType service, TimeSlot blocked, String referenceId,
                                       LocalDateTime holdUntil) {
        Map<LocalDateTime, Long> needed = blocked.minutesPerBucket(BucketKey.BUCKET_MINUTES);
        return locks.callWith(LockKey.zone(zoneId), () -> {
            List<String> conflicts = new ArrayList<>();
            needed.forEach((start, minutes) -> {
                if (free(BucketKey.forService(zoneId, service, start)) < minutes
                        || free(BucketKey.forZone(zoneId, start)) < minutes) {
                    conflicts.add(start.toString());
                }
            });
            if (!conflicts.isEmpty()) {
                throw new ConflictException(ErrorCode.SLOT_UNAVAILABLE,
                        "No " + service + " capacity in " + zoneId + " for " + blocked.start(),
                        Map.of("zone", zoneId, "service", service.name(), "conflicts", conflicts));
            }
            needed.forEach((start, minutes) -> {
                buckets.getOrCreate(BucketKey.forService(zoneId, service, start)).reserve(minutes, utilisationFactor);
                buckets.getOrCreate(BucketKey.forZone(zoneId, start)).reserve(minutes, utilisationFactor);
            });
            return reservations.save(new CapacityReservation("cr-" + UUID.randomUUID().toString().substring(0, 8),
                    referenceId, zoneId, service, blocked, needed, holdUntil));
        });
    }

    /** Payment arrived: the hold no longer expires. */
    public CapacityReservation commit(String reservationId) {
        CapacityReservation reservation = reservations.getById(reservationId);
        locks.runWith(LockKey.zone(reservation.zoneId()), reservation::commit);
        return reservation;
    }

    /** Gives back exactly the minutes this reservation took, in both pools. */
    public CapacityReservation release(String reservationId) {
        CapacityReservation reservation = reservations.getById(reservationId);
        locks.runWith(LockKey.zone(reservation.zoneId()), () -> {
            reservation.release();
            reservation.minutesPerBucket().forEach((start, minutes) -> {
                buckets.getOrCreate(BucketKey.forService(reservation.zoneId(), reservation.service(), start)).release(minutes);
                buckets.getOrCreate(BucketKey.forZone(reservation.zoneId(), start)).release(minutes);
            });
        });
        return reservation;
    }

    private long free(BucketKey key) {
        return buckets.findById(key).map(bucket -> bucket.free(utilisationFactor)).orElse(0L);
    }
}
