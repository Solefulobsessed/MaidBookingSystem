package com.example.maidbooking.capacity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final double utilisationFactor;

    public CapacityService(CapacityBucketRepository buckets, CapacityReservationRepository reservations,
                           LockRegistry locks, ApplicationEventPublisher events, Clock clock,
                           CapacityProperties properties) {
        this.buckets = buckets;
        this.reservations = reservations;
        this.locks = locks;
        this.events = events;
        this.clock = clock;
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
        List<CapacityReservation> lapsed = new ArrayList<>();
        try {
            return locks.callWith(LockKey.zone(zoneId), () -> {
                lapsed.addAll(releaseExpiredInZone(zoneId));
                return reserveFree(zoneId, service, blocked, referenceId, holdUntil, needed);
            });
        } finally {
            announce(lapsed);
        }
    }

    /** Releases every lapsed hold, zone by zone; called by {@link HoldSweeper}. */
    public int releaseExpiredHolds() {
        List<String> zones = reservations.findExpired(LocalDateTime.now(clock)).stream()
                .map(CapacityReservation::zoneId).distinct().toList();
        int released = 0;
        for (String zoneId : zones) {
            List<CapacityReservation> lapsed = locks.callWith(LockKey.zone(zoneId), () -> releaseExpiredInZone(zoneId));
            announce(lapsed);
            released += lapsed.size();
        }
        return released;
    }

    private CapacityReservation reserveFree(String zoneId, ServiceType service, TimeSlot blocked, String referenceId,
                                            LocalDateTime holdUntil, Map<LocalDateTime, Long> needed) {
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
    }

    /**
     * How many more jobs needing {@code blocked} would fit right now, in both pools: the smallest, over the
     * buckets the job touches, of free minutes divided by the minutes the job needs there. Read-only.
     */
    public long headroom(String zoneId, ServiceType service, TimeSlot blocked) {
        Map<LocalDateTime, Long> needed = blocked.minutesPerBucket(BucketKey.BUCKET_MINUTES);
        return locks.callWith(LockKey.zone(zoneId), () -> needed.entrySet().stream()
                .mapToLong(e -> Math.max(0, Math.min(free(BucketKey.forService(zoneId, service, e.getKey())),
                        free(BucketKey.forZone(zoneId, e.getKey())))) / e.getValue())
                .min().orElse(0));
    }

    /** Payment arrived: the hold no longer expires. A hold that has already lapsed is released instead. */
    public CapacityReservation commit(String reservationId) {
        CapacityReservation reservation = reservations.getById(reservationId);
        boolean lapsed = locks.callWith(LockKey.zone(reservation.zoneId()), () -> {
            if (reservation.isExpired(LocalDateTime.now(clock))) {
                giveBack(reservation);
                return true;
            }
            reservation.commit();
            return false;
        });
        if (lapsed) {
            announce(List.of(reservation));
            throw new ConflictException(ErrorCode.HOLD_EXPIRED, "The hold on this slot expired at " + reservation.expiresAt(),
                    Map.of("reservationId", reservationId));
        }
        return reservation;
    }

    /** Gives back exactly the minutes this reservation took, in both pools. */
    public CapacityReservation release(String reservationId) {
        CapacityReservation reservation = reservations.getById(reservationId);
        locks.runWith(LockKey.zone(reservation.zoneId()), () -> giveBack(reservation));
        return reservation;
    }

    /** Like {@link #release}, but does nothing if the reservation is already released (e.g. its hold lapsed). */
    public CapacityReservation releaseIfLive(String reservationId) {
        CapacityReservation reservation = reservations.getById(reservationId);
        locks.runWith(LockKey.zone(reservation.zoneId()), () -> {
            if (reservation.status() != ReservationStatus.RELEASED) {
                giveBack(reservation);
            }
        });
        return reservation;
    }

    /** Call under the zone lock. */
    private List<CapacityReservation> releaseExpiredInZone(String zoneId) {
        List<CapacityReservation> lapsed = reservations.findExpiredInZone(zoneId, LocalDateTime.now(clock));
        lapsed.forEach(this::giveBack);
        return lapsed;
    }

    private void giveBack(CapacityReservation reservation) {
        reservation.release();
        reservation.minutesPerBucket().forEach((start, minutes) -> {
            buckets.getOrCreate(BucketKey.forService(reservation.zoneId(), reservation.service(), start)).release(minutes);
            buckets.getOrCreate(BucketKey.forZone(reservation.zoneId(), start)).release(minutes);
        });
    }

    /** Published outside the zone lock so listeners may take any lock. */
    private void announce(List<CapacityReservation> lapsed) {
        lapsed.forEach(r -> events.publishEvent(new CapacityHoldExpired(r.id(), r.referenceId(), r.zoneId())));
    }

    private long free(BucketKey key) {
        return buckets.findById(key).map(bucket -> bucket.free(utilisationFactor)).orElse(0L);
    }

    /**
     * The smallest share (0 to 1) of sellable minutes still free, in either pool, over the buckets {@code slot}
     * touches; 0 where nothing is sellable. Used by surge pricing. Read-only.
     */
    public double freeShare(String zoneId, ServiceType service, TimeSlot slot) {
        return locks.callWith(LockKey.zone(zoneId), () -> slot.minutesPerBucket(BucketKey.BUCKET_MINUTES).keySet()
                .stream()
                .flatMap(start -> Stream.of(BucketKey.forService(zoneId, service, start),
                        BucketKey.forZone(zoneId, start)))
                .mapToDouble(key -> buckets.findById(key).map(this::freeShare).orElse(0.0))
                .min().orElse(0.0));
    }

    private double freeShare(CapacityBucket bucket) {
        double sellable = Math.floor(bucket.capacityMinutes() * utilisationFactor);
        return sellable <= 0 ? 0.0 : Math.max(0, bucket.free(utilisationFactor)) / sellable;
    }
}
