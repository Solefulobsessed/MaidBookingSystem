package com.example.maidbooking.pricing;

import java.util.Objects;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/** What is being priced: the job's own time (no travel buffer), service, tier, zone, and whether it's instant. */
public record PriceQuery(ServiceType service, Tier tier, TimeSlot slot, String zoneId, boolean instant) {

    public PriceQuery {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(zoneId, "zoneId");
    }
}
