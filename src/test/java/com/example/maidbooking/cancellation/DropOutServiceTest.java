package com.example.maidbooking.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentRepository;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.allocation.BestFitAllocationStrategy;
import com.example.maidbooking.allocation.CandidateFinder;
import com.example.maidbooking.allocation.scoring.BestFitGapRule;
import com.example.maidbooking.allocation.scoring.MaidScorer;
import com.example.maidbooking.allocation.scoring.ScoringProperties;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftChanged;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class DropOutServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot TEN_TO_ELEVEN = slot("10:00", "11:00");

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final JobRepository jobs = new JobRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final AllocationService allocation = new AllocationService(
            new CandidateFinder(zones, shifts, maids, assignments, new ZoneDistanceTravelTimeEstimator(15, 30)),
            new BestFitAllocationStrategy(new MaidScorer(List.of(new BestFitGapRule()), new ScoringProperties(Map.of()))),
            (zoneId, service, duration, constraints, now) -> List.of(), assignments, shifts, zones,
            new InMemoryLockRegistry());
    private final DropOutService dropOuts = new DropOutService(jobs, bookings, assignments, allocation, new JobGuard());

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
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
        bookings.save(new Booking("b-1", "c-1", "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), null, null, MON.minusDays(1).atTime(9, 0)));
    }

    /** A paid job at 10:00 given to {@code maidId}. */
    private Job assignedTo(String maidId) {
        Job job = jobs.save(new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, TEN_TO_ELEVEN, Money.of(249), "cr-1",
                "4821", null));
        job.confirm("p-1");
        Assignment assignment = allocation.allocateOnly(maidId, new AllocationRequest("j-1", "kor-5",
                ServiceType.CLEANING, TEN_TO_ELEVEN, MaidFilter.all(List.of()), null, null), AssignmentStatus.ACTIVE)
                .orElseThrow();
        job.assign(maidId, assignment.id());
        return job;
    }

    @Test
    void aDroppedJobGoesStraightToAnotherMaid() {
        Job job = assignedTo("m-01");

        dropOuts.dropJob("m-01", "j-1");

        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(job.maidId()).isEqualTo("m-02");
        assertThat(job.reservationId()).as("capacity untouched").isEqualTo("cr-1");
        assertThat(job.paymentId()).isEqualTo("p-1");
        assertThat(assignments.findLiveByMaid("m-01")).isEmpty();
        assertThat(assignments.findLiveByMaid("m-02")).singleElement()
                .satisfies(a -> assertThat(a.referenceId()).isEqualTo("j-1"));
    }

    @Test
    void whenNobodyElseIsFreeTheJobWaitsAndNeverGoesBackToThoseWhoDroppedIt() {
        Job job = assignedTo("m-01");
        dropOuts.dropJob("m-01", "j-1");

        dropOuts.dropJob("m-02", "j-1");

        assertThat(job.status()).isEqualTo(JobStatus.CONFIRMED);
        assertThat(job.maidId()).isNull();
        assertThat(job.droppedBy()).containsExactlyInAnyOrder("m-01", "m-02");
        assertThat(assignments.findLiveByReference("j-1")).isEmpty();
    }

    @Test
    void onlyTheAssignedMaidCanDropAJob() {
        assignedTo("m-01");

        assertThatThrownBy(() -> dropOuts.dropJob("m-02", "j-1")).isInstanceOf(ConflictException.class);
        assertThat(jobs.getById("j-1").maidId()).isEqualTo("m-01");
    }

    @Test
    void leaveOnTheDayHandsHerJobsToSomeoneElse() {
        Job job = assignedTo("m-01");
        Shift shift = shifts.getById("s-m-01");
        shift.cancel();

        dropOuts.onShiftChanged(ShiftChanged.removed(shift));

        assertThat(job.maidId()).isEqualTo("m-02");
        assertThat(job.droppedBy()).containsExactly("m-01");
    }

    @Test
    void addedCapacityAndOtherShiftsChangeNothing() {
        Job job = assignedTo("m-01");

        dropOuts.onShiftChanged(ShiftChanged.added(shifts.getById("s-m-01")));
        dropOuts.onShiftChanged(ShiftChanged.removed(shifts.getById("s-m-02")));

        assertThat(job.maidId()).isEqualTo("m-01");
    }

    @Test
    void anUnpaidJobJustLosesItsHeldMaidUntilItIsPaid() {
        Job job = jobs.save(new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, TEN_TO_ELEVEN, Money.of(249), "cr-1",
                "4821", MON.minusDays(1).atTime(18, 10)));
        Assignment held = allocation.allocateOnly("m-01", new AllocationRequest("j-1", "kor-5", ServiceType.CLEANING,
                TEN_TO_ELEVEN, MaidFilter.all(List.of()), null, null), AssignmentStatus.HELD).orElseThrow();
        job.holdMaid("m-01", held.id());

        dropOuts.dropJob("m-01", "j-1");

        assertThat(job.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        assertThat(job.maidId()).isNull();
        assertThat(job.droppedBy()).containsExactly("m-01");
        assertThat(held.status()).isEqualTo(AssignmentStatus.RELEASED);
    }
}
