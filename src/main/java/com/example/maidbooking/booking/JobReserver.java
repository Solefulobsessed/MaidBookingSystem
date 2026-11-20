package com.example.maidbooking.booking;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

/** Turns one planned visit into an unpaid job: prices it and reserves its capacity, travel buffer included. */
@Component
public class JobReserver {

    private final ZoneRepository zones;
    private final TravelTimeEstimator travel;
    private final PricingStrategy pricing;
    private final CapacityService capacity;
    private final SecureRandom random = new SecureRandom();

    public JobReserver(ZoneRepository zones, TravelTimeEstimator travel, PricingStrategy pricing,
                       CapacityService capacity) {
        this.zones = zones;
        this.travel = travel;
        this.pricing = pricing;
        this.capacity = capacity;
    }

    /**
     * @param holdUntil when the unpaid reservation lapses; {@code null} keeps it until the job is charged
     * @throws com.example.maidbooking.common.error.ConflictException SLOT_UNAVAILABLE if the zone has no room
     */
    public Job reserve(String bookingId, String zoneId, ServiceType service, Tier tier, TimeSlot slot,
                       LocalDateTime holdUntil, boolean instant) {
        Zone zone = zones.getById(zoneId);
        Duration buffer = travel.estimate(zone, zone).orElseThrow();
        String jobId = "j-" + UUID.randomUUID().toString().substring(0, 8);
        Money price = pricing.price(new PriceQuery(service, tier, slot, zoneId, instant));
        String reservationId = capacity.reserve(zoneId, service, slot.withBufferBefore(buffer), jobId, holdUntil).id();
        return new Job(jobId, bookingId, zoneId, service, slot, price, reservationId,
                "%04d".formatted(random.nextInt(10_000)), holdUntil);
    }
}
