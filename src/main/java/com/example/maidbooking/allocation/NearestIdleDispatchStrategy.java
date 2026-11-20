package com.example.maidbooking.allocation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.LiveStatus;
import com.example.maidbooking.supply.MaidLiveState;
import com.example.maidbooking.supply.MaidLiveStateRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.ShiftStatus;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

/**
 * Snabbit-style dispatch: maids checked in and IDLE in the job's zone or next door, who can arrive within the
 * promise (default 15 min) and whose shift and calendar fit the whole job. Nearest first.
 */
@Component
public class NearestIdleDispatchStrategy implements DispatchStrategy {

    private static final int ROUND_TO_MINUTES = 5;

    private final ZoneRepository zones;
    private final MaidLiveStateRepository liveStates;
    private final ShiftRepository shifts;
    private final CandidateFinder finder;
    private final TravelTimeEstimator travel;
    private final Duration promise;

    public NearestIdleDispatchStrategy(ZoneRepository zones, MaidLiveStateRepository liveStates, ShiftRepository shifts,
                                       CandidateFinder finder, TravelTimeEstimator travel,
                                       @Value("${app.instant.promise-minutes:15}") long promiseMinutes) {
        this.zones = zones;
        this.liveStates = liveStates;
        this.shifts = shifts;
        this.finder = finder;
        this.travel = travel;
        this.promise = Duration.ofMinutes(promiseMinutes);
    }

    @Override
    public List<DispatchOption> options(String zoneId, ServiceType service, Duration duration, MaidFilter constraints,
                                        LocalDateTime now) {
        Zone target = zones.getById(zoneId);
        List<DispatchOption> options = new ArrayList<>();
        for (Zone zone : zones.findAll()) {
            if (!zone.id().equals(zoneId) && !zone.isNeighbourOf(target)) {
                continue;
            }
            for (MaidLiveState idle : liveStates.findByStatusInZone(LiveStatus.IDLE, zone.id())) {
                travel.estimate(zone, target).filter(eta -> eta.compareTo(promise) <= 0)
                        .flatMap(eta -> option(idle.maidId(), target, service, duration, constraints, now, eta))
                        .ifPresent(options::add);
            }
        }
        options.sort(Comparator.comparing(DispatchOption::eta).thenComparing(o -> o.candidate().maid().id()));
        return options;
    }

    private Optional<DispatchOption> option(String maidId, Zone target, ServiceType service, Duration duration,
                                            MaidFilter constraints, LocalDateTime now, Duration eta) {
        LocalDateTime start = roundUp(now).plus(eta);
        AllocationRequest request = new AllocationRequest("instant", target.id(), service,
                TimeSlot.of(start, duration), constraints, null, null);
        return shifts.findByMaidAndDate(maidId, now.toLocalDate()).stream()
                .filter(shift -> shift.status() == ShiftStatus.CHECKED_IN)
                .findFirst()
                .flatMap((Shift shift) -> finder.candidate(shift, target, request))
                .map(candidate -> new DispatchOption(candidate, request.work(), Duration.between(now, start)));
    }

    static LocalDateTime roundUp(LocalDateTime time) {
        LocalDateTime minute = time.truncatedTo(ChronoUnit.MINUTES);
        int over = minute.getMinute() % ROUND_TO_MINUTES;
        LocalDateTime rounded = over == 0 ? minute : minute.plusMinutes(ROUND_TO_MINUTES - over);
        return rounded.isBefore(time) ? rounded.plusMinutes(ROUND_TO_MINUTES) : rounded;
    }
}
