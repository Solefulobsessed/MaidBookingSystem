package com.example.maidbooking.allocation.scoring;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.Candidate;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

/**
 * Prefers the maid who is closest: travel from her previous job's zone (or her shift's zone if it's her
 * first job) to the job's zone. 0 min = 1.0, 15 = 0.75, 30 = 0.5, an hour or out of reach = 0.
 */
@Component
public class TravelDistanceRule implements ScoringRule {

    private static final double FULL_PENALTY_MINUTES = 60.0;

    private final ZoneRepository zones;
    private final TravelTimeEstimator travel;

    public TravelDistanceRule(ZoneRepository zones, TravelTimeEstimator travel) {
        this.zones = zones;
        this.travel = travel;
    }

    @Override
    public String name() {
        return "travel-distance";
    }

    @Override
    public double score(Candidate candidate, AllocationRequest request) {
        String fromZone = candidate.calendar().previousBefore(request.work().start())
                .map(Assignment::zoneId).orElse(candidate.shift().zoneId());
        return travel.estimate(zones.getById(fromZone), zones.getById(request.zoneId()))
                .map(Duration::toMinutes)
                .map(minutes -> Math.max(0.0, 1.0 - minutes / FULL_PENALTY_MINUTES))
                .orElse(0.0);
    }
}
