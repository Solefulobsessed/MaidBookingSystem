package com.example.maidbooking.capacity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.supply.ServiceType;

/**
 * Minutes promised to one job, per bucket. Releasing gives back exactly these minutes, so a
 * release always undoes its own reserve.
 */
public class CapacityReservation {

    private final String id;
    private final String referenceId;
    private final String zoneId;
    private final ServiceType service;
    private final TimeSlot blocked;
    private final Map<LocalDateTime, Long> minutesPerBucket;
    private ReservationStatus status = ReservationStatus.HELD;
    private LocalDateTime expiresAt;

    public CapacityReservation(String id, String referenceId, String zoneId, ServiceType service, TimeSlot blocked,
                               Map<LocalDateTime, Long> minutesPerBucket, LocalDateTime expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.service = Objects.requireNonNull(service, "service");
        this.blocked = Objects.requireNonNull(blocked, "blocked");
        this.minutesPerBucket = Map.copyOf(minutesPerBucket);
        this.expiresAt = expiresAt;
    }

    public void commit() {
        if (status != ReservationStatus.HELD) {
            throw new InvalidStateTransitionException("Capacity reservation", status, "commit");
        }
        status = ReservationStatus.COMMITTED;
        expiresAt = null;
    }

    public void release() {
        if (status == ReservationStatus.RELEASED) {
            throw new InvalidStateTransitionException("Capacity reservation", status, "release");
        }
        status = ReservationStatus.RELEASED;
    }

    /** Only a HELD reservation with an expiry can lapse. */
    public boolean isExpired(LocalDateTime now) {
        return status == ReservationStatus.HELD && expiresAt != null && !now.isBefore(expiresAt);
    }

    public String id() { return id; }
    public String referenceId() { return referenceId; }
    public String zoneId() { return zoneId; }
    public ServiceType service() { return service; }
    public TimeSlot blocked() { return blocked; }
    public Map<LocalDateTime, Long> minutesPerBucket() { return minutesPerBucket; }
    public ReservationStatus status() { return status; }
    public LocalDateTime expiresAt() { return expiresAt; }
}
