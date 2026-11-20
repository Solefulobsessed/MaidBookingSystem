package com.example.maidbooking.capacity;

/** HELD until the booking is paid (or with no expiry for later recurring jobs), then COMMITTED; RELEASED when freed. */
public enum ReservationStatus {
    HELD,
    COMMITTED,
    RELEASED
}
