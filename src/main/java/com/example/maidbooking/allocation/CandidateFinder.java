package com.example.maidbooking.allocation;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

/**
 * Every maid who could do a job: on a live shift in the job's zone or a neighbouring zone, active, offering
 * the service, meeting the customer's constraints, not excluded, and with room in her day for the travel
 * there, the job, and the travel on to her next job.
 */
@Component
public class CandidateFinder {

    private final ZoneRepository zones;
    private final ShiftRepository shifts;
    private final MaidRepository maids;
    private final AssignmentRepository assignments;
    private final TravelTimeEstimator travel;

    public CandidateFinder(ZoneRepository zones, ShiftRepository shifts, MaidRepository maids,
                           AssignmentRepository assignments, TravelTimeEstimator travel) {
        this.zones = zones;
        this.shifts = shifts;
        this.maids = maids;
        this.assignments = assignments;
        this.travel = travel;
    }

    public List<Candidate> find(AllocationRequest request) {
        Zone target = zones.getById(request.zoneId());
        LocalDate date = request.work().start().toLocalDate();
        Map<String, Candidate> byMaid = new LinkedHashMap<>();
        for (Zone zone : searchZones(target)) {
            for (Shift shift : shifts.findByZoneAndDate(zone.id(), date)) {
                if (!byMaid.containsKey(shift.maidId())) {
                    candidate(shift, target, request).ifPresent(c -> byMaid.put(shift.maidId(), c));
                }
            }
        }
        return new ArrayList<>(byMaid.values());
    }

    /** Builds a candidate if this maid on this shift can do the job. Also used to re-check under her lock. */
    public Optional<Candidate> candidate(Shift shift, Zone target, AllocationRequest request) {
        Maid maid = maids.getById(shift.maidId());
        if (!shift.addsCapacity() || !maid.isActive() || !maid.offers(request.service())
                || request.excludedMaidIds().contains(maid.id()) || !request.constraints().matches(maid)) {
            return Optional.empty();
        }
        MaidCalendar calendar = new MaidCalendar(maid.id(), assignments.findLiveByMaid(maid.id()));
        String fromZone = calendar.previousBefore(request.work().start()).map(Assignment::zoneId).orElse(shift.zoneId());
        Optional<Duration> travelThere = travel.estimate(zones.getById(fromZone), target);
        Optional<Duration> travelOn = calendar.nextAfter(request.work().end())
                .map(next -> travel.estimate(target, zones.getById(next.zoneId())))
                .orElse(Optional.of(Duration.ZERO));
        if (travelThere.isEmpty() || travelOn.isEmpty()) {
            return Optional.empty();
        }
        TimeSlot blocked = request.work().withBufferBefore(travelThere.get());
        if (!shift.window().contains(blocked) || !calendar.fits(blocked, travelOn.get())) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(maid, shift, calendar, blocked));
    }

    /** The job's own zone first, then its neighbours in id order. */
    private List<Zone> searchZones(Zone target) {
        List<Zone> result = new ArrayList<>(List.of(target));
        zones.findAll().stream().filter(zone -> zone.isNeighbourOf(target))
                .sorted(Comparator.comparing(Zone::id)).forEach(result::add);
        return result;
    }
}
