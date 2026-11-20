package com.example.maidbooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import com.example.maidbooking.allocation.scoring.BestFitGapRule;
import com.example.maidbooking.allocation.scoring.MaidScorer;
import com.example.maidbooking.allocation.scoring.ScoringProperties;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.common.lock.LockRegistry;
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

/**
 * Many allocations at the same instant must never give one maid two jobs that overlap (travel included).
 * Repeated 20 times because races are probabilistic.
 */
class AllocationConcurrencyTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final int THREADS = 50;

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void shutDown() throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    AllocationService service(LockRegistry locks) {
        CandidateFinder finder = new CandidateFinder(zones, shifts, maids, assignments,
                new ZoneDistanceTravelTimeEstimator(15, 30));
        AllocationStrategy strategy = new BestFitAllocationStrategy(
                new MaidScorer(List.of(new BestFitGapRule()), new ScoringProperties(Map.of())));
        return new AllocationService(finder, strategy, (zoneId, service, duration, constraints, now) -> List.of(),
                assignments, shifts, zones, locks);
    }

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
    }

    private void maidsOnShift(int count) {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        for (int i = 1; i <= count; i++) {
            String id = String.format("m-%02d", i);
            Maid maid = new Maid(id, "Maid " + id, Gender.FEMALE, "98765432" + id.substring(2),
                    Set.of(ServiceType.CLEANING), null, Tier.STANDARD, "kor-5", 4.5);
            maid.activate();
            maids.save(maid);
            shifts.save(new Shift("s-" + id, id, "kor-5", slot("07:00", "13:00")));
        }
    }

    /** Runs one allocation per slot, all released at once; returns how many got a maid. */
    int raceAll(AllocationService service, List<TimeSlot> jobs) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Assignment>>> results = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            AllocationRequest request = new AllocationRequest("j-" + i, "kor-5", ServiceType.CLEANING, jobs.get(i),
                    MaidFilter.all(List.of()), null, null);
            results.add(pool.submit(() -> {
                start.await();
                return service.allocate(request, AssignmentStatus.ACTIVE);
            }));
        }
        start.countDown();
        int assigned = 0;
        for (Future<Optional<Assignment>> result : results) {
            assigned += result.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0;
        }
        return assigned;
    }

    void assertNoMaidDoubleBooked() {
        Map<String, List<Assignment>> byMaid = assignments.findAll().stream().filter(Assignment::isLive)
                .collect(Collectors.groupingBy(Assignment::maidId));
        byMaid.forEach((maidId, jobs) -> {
            for (int i = 0; i < jobs.size(); i++) {
                for (int j = i + 1; j < jobs.size(); j++) {
                    assertThat(jobs.get(i).blocked().overlaps(jobs.get(j).blocked()))
                            .as("%s has overlapping jobs %s and %s", maidId, jobs.get(i).work(), jobs.get(j).work())
                            .isFalse();
                }
            }
        });
    }

    @RepeatedTest(20)
    void oneMaidFiftyOverlappingJobsOnlyOneIsAssigned() throws Exception {
        maidsOnShift(1);
        List<TimeSlot> jobs = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            jobs.add(i % 2 == 0 ? slot("10:00", "11:00") : slot("10:30", "11:30"));
        }

        assertThat(raceAll(service(new InMemoryLockRegistry()), jobs)).isEqualTo(1);
        assertNoMaidDoubleBooked();
    }

    @RepeatedTest(20)
    void threeMaidsFiftyCustomersForTheSameHourExactlyThreeAssigned() throws Exception {
        maidsOnShift(3);
        List<TimeSlot> jobs = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            jobs.add(slot("10:00", "11:00"));
        }

        assertThat(raceAll(service(new InMemoryLockRegistry()), jobs)).isEqualTo(3);
        assertThat(assignments.findAll().stream().map(Assignment::maidId).distinct()).hasSize(3);
        assertNoMaidDoubleBooked();
    }

    @RepeatedTest(20)
    void backToBackJobsForOneMaidAllFitWhateverTheOrder() throws Exception {
        maidsOnShift(1);
        List<TimeSlot> jobs = List.of(slot("07:15", "08:00"), slot("08:15", "09:00"), slot("09:15", "10:15"),
                slot("10:30", "11:30"), slot("11:45", "12:45"));

        assertThat(raceAll(service(new InMemoryLockRegistry()), jobs)).isEqualTo(5);
        assertNoMaidDoubleBooked();
    }
}
