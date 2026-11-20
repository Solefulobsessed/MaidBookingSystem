package com.example.maidbooking.zone;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fixed estimates by zone relationship: same zone, neighbouring zone, or unreachable. No maps API. */
@Component
public class ZoneDistanceTravelTimeEstimator implements TravelTimeEstimator {

    private final Duration sameZone;
    private final Duration neighbourZone;

    public ZoneDistanceTravelTimeEstimator(@Value("${app.travel.same-zone-minutes:15}") long sameZoneMinutes,
                                           @Value("${app.travel.neighbour-zone-minutes:30}") long neighbourMinutes) {
        this.sameZone = Duration.ofMinutes(sameZoneMinutes);
        this.neighbourZone = Duration.ofMinutes(neighbourMinutes);
    }

    @Override
    public Optional<Duration> estimate(Zone from, Zone to) {
        if (from.id().equals(to.id())) {
            return Optional.of(sameZone);
        }
        return from.isNeighbourOf(to) ? Optional.of(neighbourZone) : Optional.empty();
    }
}
