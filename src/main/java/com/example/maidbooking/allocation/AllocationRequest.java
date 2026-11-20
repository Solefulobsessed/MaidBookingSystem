package com.example.maidbooking.allocation;

import java.util.Objects;
import java.util.Set;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.ServiceType;

/**
 * A job that needs a maid. Allocation works from this, not from the booking's Job, so the allocation
 * package doesn't depend on bookings.
 *
 * @param constraints     the customer's hard requirements (gender, rating, ...)
 * @param preferredMaidId a maid to favour, or null
 * @param excludedMaidIds maids who must not get it, e.g. one who just dropped it
 */
public record AllocationRequest(String referenceId, String zoneId, ServiceType service, TimeSlot work,
                                MaidFilter constraints, String preferredMaidId, Set<String> excludedMaidIds) {

    public AllocationRequest {
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(constraints, "constraints");
        excludedMaidIds = excludedMaidIds == null ? Set.of() : Set.copyOf(excludedMaidIds);
    }
}
