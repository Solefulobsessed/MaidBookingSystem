package com.example.maidbooking.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.DispatchOption;
import com.example.maidbooking.allocation.DispatchStrategy;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneResolver;

/** {@code GET /slots/instant}: can someone come now? Nothing is reserved; booking re-checks everything. */
@Service
public class InstantAvailabilityService {

    static final int ALTERNATIVES = 3;

    private final ZoneResolver zones;
    private final DispatchStrategy dispatch;
    private final CapacityService capacity;
    private final PricingStrategy pricing;
    private final SlotService slots;
    private final Clock clock;

    public InstantAvailabilityService(ZoneResolver zones, DispatchStrategy dispatch, CapacityService capacity,
                                      PricingStrategy pricing, SlotService slots, Clock clock) {
        this.zones = zones;
        this.dispatch = dispatch;
        this.capacity = capacity;
        this.pricing = pricing;
        this.slots = slots;
        this.clock = clock;
    }

    public InstantAvailability check(String locality, ServiceType service, int durationMinutes, Tier tier) {
        JobDurations.validate(durationMinutes);
        Zone zone = zones.resolve(locality);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<DispatchOption> nearest = dispatch.options(zone.id(), service, Duration.ofMinutes(durationMinutes),
                        MaidFilter.all(List.of()), now).stream()
                .filter(option -> capacity.headroom(zone.id(), service, option.candidate().blocked()) > 0)
                .findFirst();
        if (nearest.isPresent()) {
            DispatchOption option = nearest.get();
            return new InstantAvailability(true, option.work().start(), option.eta().toMinutes(),
                    pricing.price(new PriceQuery(service, tier, option.work(), zone.id(), true)), List.of());
        }
        return new InstantAvailability(false, null, null, null,
                alternatives(locality, service, durationMinutes, tier, now.toLocalDate()));
    }

    /** The next few scheduled slots today or tomorrow that are not full, to offer when nobody can come now. */
    public List<InstantAvailability.Alternative> alternatives(String locality, ServiceType service, int minutes,
                                                               Tier tier, LocalDate today) {
        List<InstantAvailability.Alternative> open = new ArrayList<>();
        for (LocalDate day = today; open.size() < ALTERNATIVES && !day.isAfter(today.plusDays(1)); day = day.plusDays(1)) {
            LocalDate date = day;
            slots.slots(locality, service, date, minutes, tier).slots().stream()
                    .filter(slot -> slot.status() != SlotStatus.FULL)
                    .limit(ALTERNATIVES - open.size())
                    .forEach(slot -> open.add(new InstantAvailability.Alternative(date.atTime(slot.start()),
                            slot.status(), slot.price())));
        }
        return open;
    }
}
