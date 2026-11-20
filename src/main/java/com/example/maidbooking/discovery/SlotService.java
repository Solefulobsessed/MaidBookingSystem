package com.example.maidbooking.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneResolver;

/** The slot picker: every 30-minute start from 07:00 to 20:00, read from the capacity ledger. */
@Service
public class SlotService {

    static final LocalTime FIRST_START = LocalTime.of(7, 0);
    static final LocalTime LAST_START = LocalTime.of(20, 0);
    static final int STEP_MINUTES = 30;

    private final ZoneResolver zones;
    private final CapacityService capacity;
    private final PricingStrategy pricing;
    private final TravelTimeEstimator travel;
    private final Clock clock;
    private final Duration minLead;

    public SlotService(ZoneResolver zones, CapacityService capacity, PricingStrategy pricing, TravelTimeEstimator travel,
                       Clock clock, @Value("${app.booking.min-lead-minutes:120}") long minLeadMinutes) {
        this.zones = zones;
        this.capacity = capacity;
        this.pricing = pricing;
        this.travel = travel;
        this.clock = clock;
        this.minLead = Duration.ofMinutes(minLeadMinutes);
    }

    public SlotGrid slots(String locality, ServiceType service, LocalDate date, int durationMinutes, Tier tier) {
        JobDurations.validate(durationMinutes);
        Zone zone = zones.resolve(locality);
        Duration buffer = travel.estimate(zone, zone).orElseThrow();
        LocalDateTime earliest = LocalDateTime.now(clock).plus(minLead);
        List<SlotGrid.Slot> slots = new ArrayList<>();
        for (LocalTime start = FIRST_START; !start.isAfter(LAST_START); start = start.plusMinutes(STEP_MINUTES)) {
            TimeSlot job = TimeSlot.of(date.atTime(start), Duration.ofMinutes(durationMinutes));
            if (job.start().isBefore(earliest) || !job.end().toLocalDate().equals(date)) {
                continue;
            }
            long headroom = capacity.headroom(zone.id(), service, job.withBufferBefore(buffer));
            SlotStatus status = headroom == 0 ? SlotStatus.FULL : headroom < 2 ? SlotStatus.FEW_LEFT : SlotStatus.OPEN;
            slots.add(new SlotGrid.Slot(start, status, status == SlotStatus.FULL ? null
                    : pricing.price(new PriceQuery(service, tier, job, zone.id(), false))));
        }
        return new SlotGrid(zone.id(), date, slots);
    }
}
