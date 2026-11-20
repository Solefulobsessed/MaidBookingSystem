package com.example.maidbooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.scoring.BestFitGapRule;
import com.example.maidbooking.allocation.scoring.MaidScorer;
import com.example.maidbooking.allocation.scoring.PreferredMaidRule;
import com.example.maidbooking.allocation.scoring.ScoringProperties;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class AllocationServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot TEN_TO_ELEVEN = slot("10:00", "11:00");

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final CandidateFinder finder = new CandidateFinder(zones, shifts, maids, assignments,
            new ZoneDistanceTravelTimeEstimator(15, 30));
    private final AllocationStrategy bestFit = new BestFitAllocationStrategy(new MaidScorer(
            List.of(new BestFitGapRule(), new PreferredMaidRule()),
            new ScoringProperties(Map.of("best-fit-gap", 1.0, "preferred-maid", 10.0))));

    private AllocationService service(AllocationStrategy strategy) {
        return new AllocationService(finder, strategy, assignments, shifts, zones, new InMemoryLockRegistry());
    }

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
    }

    private static AllocationRequest request(String jobId, TimeSlot work, String preferredMaidId) {
        return new AllocationRequest(jobId, "kor-5", ServiceType.CLEANING, work, MaidFilter.all(List.of()),
                preferredMaidId, null);
    }

    private void busy(String maidId, String from, String to) {
        TimeSlot work = slot(from, to);
        assignments.save(new Assignment("a-" + maidId + from, "j-old-" + maidId + from, maidId, "s-" + maidId, "kor-5",
                work, work.withBufferBefore(Duration.ofMinutes(15)), AssignmentStatus.ACTIVE));
    }

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        for (String id : List.of("m-01", "m-02")) {
            Maid maid = new Maid(id, "Maid " + id, Gender.FEMALE, "98765432" + id.substring(2),
                    Set.of(ServiceType.CLEANING), null, Tier.STANDARD, "kor-5", 4.5);
            maid.activate();
            maids.save(maid);
            shifts.save(new Shift("s-" + id, id, "kor-5", slot("07:00", "13:00")));
        }
        // m-01's 10:00-11:00 gap is snug; m-02 is free all morning.
        busy("m-01", "08:30", "09:30");
        busy("m-01", "11:15", "12:15");
    }

    @Test
    void assignsTheBestFittingMaidAndSavesHerBlockedTime() {
        Assignment assignment = service(bestFit).allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE)
                .orElseThrow();

        assertThat(assignment.maidId()).isEqualTo("m-01");
        assertThat(assignment.blocked()).isEqualTo(slot("09:45", "11:00"));
        assertThat(assignment.status()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(assignments.findLiveByReference("j-1")).contains(assignment);
    }

    @Test
    void nextOverlappingJobGoesToTheOtherMaidThenNobodyIsLeft() {
        AllocationService allocation = service(bestFit);

        assertThat(allocation.allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE))
                .map(Assignment::maidId).contains("m-01");
        assertThat(allocation.allocate(request("j-2", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE))
                .map(Assignment::maidId).contains("m-02");
        assertThat(allocation.allocate(request("j-3", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE)).isEmpty();
    }

    @Test
    void preferredMaidWinsWhenFree() {
        assertThat(service(bestFit).allocate(request("j-1", TEN_TO_ELEVEN, "m-02"), AssignmentStatus.ACTIVE))
                .map(Assignment::maidId).contains("m-02");
    }

    @Test
    void allocatingTheSameJobTwiceReturnsTheSameAssignment() {
        AllocationService allocation = service(bestFit);
        Assignment first = allocation.allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE).orElseThrow();

        assertThat(allocation.allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE)).contains(first);
        assertThat(assignments.findLiveByMaid("m-02")).isEmpty();
    }

    @Test
    void ifTheTopMaidIsTakenAfterRankingTheNextOneGetsTheJob() {
        AllocationStrategy rankThenLoseTopMaid = (candidates, request) -> {
            List<Candidate> ordered = bestFit.order(candidates, request);
            busy(ordered.get(0).maid().id(), "10:00", "11:00");  // another booking grabs her meanwhile
            return ordered;
        };

        Assignment assignment = service(rankThenLoseTopMaid)
                .allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE).orElseThrow();

        assertThat(assignment.maidId()).isEqualTo("m-02");
        assertThat(assignments.findLiveByMaid("m-01")).hasSize(3).noneMatch(a -> a.referenceId().equals("j-1"));
    }

    @Test
    void heldInstantAssignmentCanBeActivatedOrReleased() {
        AllocationService allocation = service(bestFit);
        Assignment held = allocation.allocate(request("j-1", TEN_TO_ELEVEN, null), AssignmentStatus.HELD).orElseThrow();

        allocation.activate("j-1");
        assertThat(held.status()).isEqualTo(AssignmentStatus.ACTIVE);

        allocation.release("j-1");
        assertThat(held.isLive()).isFalse();
        assertThat(allocation.release("j-1")).isEmpty();
        assertThat(allocation.allocate(request("j-2", TEN_TO_ELEVEN, null), AssignmentStatus.ACTIVE))
                .as("her time is free again").map(Assignment::maidId).contains("m-01");
    }
}
