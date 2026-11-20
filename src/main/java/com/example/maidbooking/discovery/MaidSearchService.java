package com.example.maidbooking.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;
import com.example.maidbooking.allocation.CandidateFinder;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneResolver;

/**
 * Preferred-maid mode: maids who could really take this slot (same checks as allocation), narrowed by the
 * customer's filters, with each maid's quoted price. Empty when the zone has no capacity left for the slot.
 */
@Service
public class MaidSearchService {

    static final int MAX_PAGE_SIZE = 50;

    private final ZoneResolver zones;
    private final FilterRegistry filters;
    private final CandidateFinder finder;
    private final CapacityService capacity;
    private final PricingStrategy pricing;
    private final TravelTimeEstimator travel;
    private final Clock clock;
    private final Duration minLead;

    public MaidSearchService(ZoneResolver zones, FilterRegistry filters, CandidateFinder finder, CapacityService capacity,
                             PricingStrategy pricing, TravelTimeEstimator travel, Clock clock,
                             @Value("${app.booking.min-lead-minutes:120}") long minLeadMinutes) {
        this.zones = zones;
        this.filters = filters;
        this.finder = finder;
        this.capacity = capacity;
        this.pricing = pricing;
        this.travel = travel;
        this.clock = clock;
        this.minLead = Duration.ofMinutes(minLeadMinutes);
    }

    public MaidSearchResult search(String locality, ServiceType service, LocalDateTime start, int durationMinutes,
                                   Map<String, String> filterParams, int page, int size) {
        validate(start, durationMinutes, page, size);
        Zone zone = zones.resolve(locality);
        TimeSlot slot = TimeSlot.of(start, Duration.ofMinutes(durationMinutes));
        MaidFilter constraints = MaidFilter.all(filters.fromParams(filterParams));
        if (capacity.headroom(zone.id(), service, slot.withBufferBefore(travel.estimate(zone, zone).orElseThrow())) == 0) {
            return new MaidSearchResult(List.of(), page, size, 0);
        }
        List<MaidSearchResult.Item> all = finder.find(new AllocationRequest("search", zone.id(), service, slot,
                        constraints, null, null)).stream()
                .map(candidate -> item(candidate, service, slot, zone))
                .sorted(Comparator.comparingDouble(MaidSearchResult.Item::rating).reversed()
                        .thenComparing(MaidSearchResult.Item::quotedPrice)
                        .thenComparing(MaidSearchResult.Item::maidId))
                .toList();
        List<MaidSearchResult.Item> pageItems = all.stream().skip((long) page * size).limit(size).toList();
        return new MaidSearchResult(pageItems, page, size, all.size());
    }

    private MaidSearchResult.Item item(Candidate candidate, ServiceType service, TimeSlot slot, Zone zone) {
        var maid = candidate.maid();
        return new MaidSearchResult.Item(maid.id(), maid.name(), maid.gender(), maid.tier(), maid.rating(), maid.tags(),
                candidate.shift().zoneId(), pricing.price(new PriceQuery(service, maid.tier(), slot, zone.id(), false)));
    }

    private void validate(LocalDateTime start, int durationMinutes, int page, int size) {
        JobDurations.validate(durationMinutes);
        if (start.isBefore(LocalDateTime.now(clock).plus(minLead))) {
            throw new ValidationException("Start must be at least " + minLead.toMinutes() + " minutes from now");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidationException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
