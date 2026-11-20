package com.example.maidbooking.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentRepository;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.allocation.CandidateFinder;
import com.example.maidbooking.allocation.DispatchOption;
import com.example.maidbooking.allocation.NearestIdleDispatchStrategy;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.pricing.TestRateCard;
import com.example.maidbooking.pricing.TierPricing;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.LiveStatus;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidLiveState;
import com.example.maidbooking.supply.MaidLiveStateRepository;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class InstantAvailabilityServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime NOW = MON.atTime(10, 2, 30);

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final MaidLiveStateRepository liveStates = new MaidLiveStateRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final MutableClock clock = new MutableClock(NOW);
    private final ZoneDistanceTravelTimeEstimator travel = new ZoneDistanceTravelTimeEstimator(15, 30);
    private final CapacityService capacity = new CapacityService(buckets, new CapacityReservationRepository(),
            new InMemoryLockRegistry(), event -> { }, clock, new CapacityProperties(1.0));
    private NearestIdleDispatchStrategy dispatch;
    private InstantAvailabilityService instant;

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of("hsr-1")));
        zones.save(new Zone("hsr-1", "HSR Layout", Set.of("hsr layout"), Set.of()));
        dispatch = new NearestIdleDispatchStrategy(zones, liveStates, shifts,
                new CandidateFinder(zones, shifts, maids, assignments, travel), travel, 15);
        LocalityZoneResolver resolver = new LocalityZoneResolver(zones);
        TierPricing pricing = new TierPricing(TestRateCard.standard());
        instant = new InstantAvailabilityService(resolver, dispatch, capacity, pricing,
                new SlotService(resolver, capacity, pricing, travel, clock, 120), clock);
        capacityFor(MON, 7, 13, 60);
    }

    private void capacityFor(LocalDate day, int fromHour, int toHour, long minutes) {
        for (LocalDateTime t = day.atTime(fromHour, 0); t.isBefore(day.atTime(toHour, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(minutes);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(minutes);
        }
    }

    private Maid working(String id, String zone, LiveStatus status, String shiftEnd, ServiceType skill) {
        Maid maid = new Maid(id, "Maid " + id, Gender.FEMALE, "98765432" + id.substring(2), Set.of(skill), null,
                Tier.STANDARD, zone, 4.5);
        maid.activate();
        maids.save(maid);
        Shift shift = shifts.save(new Shift("s-" + id, id, zone,
                new TimeSlot(MON.atTime(7, 0), MON.atTime(LocalTime.parse(shiftEnd)))));
        shift.checkIn();
        MaidLiveState state = new MaidLiveState(id);
        state.moveTo(LiveStatus.IDLE, zone, MON.atTime(7, 0));
        if (status == LiveStatus.BREAK) {
            state.moveTo(LiveStatus.BREAK, zone, MON.atTime(10, 0));
        }
        liveStates.save(state);
        return maid;
    }

    private List<String> dispatchable() {
        return dispatch.options("kor-5", ServiceType.CLEANING, Duration.ofHours(1), MaidFilter.all(List.of()), NOW)
                .stream().map(o -> o.candidate().maid().id()).toList();
    }

    @Test
    void idleMaidInTheZoneCanComeAfterRoundingAndTravel() {
        working("m-01", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);

        DispatchOption option = dispatch.options("kor-5", ServiceType.CLEANING, Duration.ofHours(1),
                MaidFilter.all(List.of()), NOW).get(0);

        assertThat(option.work()).isEqualTo(new TimeSlot(MON.atTime(10, 20), MON.atTime(11, 20)));
        assertThat(option.eta()).isEqualTo(Duration.ofMinutes(17).plusSeconds(30));
    }

    @Test
    void onlyIdleCheckedInMaidsCloseEnoughWithRoomForTheWholeJob() {
        working("m-01", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);
        working("m-02", "hsr-1", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);
        working("m-03", "kor-5", LiveStatus.BREAK, "13:00", ServiceType.CLEANING);
        working("m-04", "kor-5", LiveStatus.IDLE, "10:30", ServiceType.CLEANING);
        working("m-05", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);
        working("m-06", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.COOKING);
        TimeSlot busy = new TimeSlot(MON.atTime(10, 45), MON.atTime(11, 45));
        assignments.save(new Assignment("a-1", "j-1", "m-05", "s-m-05", "kor-5", busy,
                busy.withBufferBefore(Duration.ofMinutes(15)), AssignmentStatus.ACTIVE));

        assertThat(dispatchable()).as("hsr is 30 min away; break; shift ends; busy; wrong skill")
                .containsExactly("m-01");
    }

    @Test
    void availableNowWithStartEtaAndPrice() {
        working("m-01", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);

        InstantAvailability result = instant.check("koramangala", ServiceType.CLEANING, 60, Tier.STANDARD);

        assertThat(result.available()).isTrue();
        assertThat(result.start()).isEqualTo(MON.atTime(10, 20));
        assertThat(result.etaMinutes()).isEqualTo(17);
        assertThat(result.price()).isEqualTo(Money.of(249));
        assertThat(result.alternatives()).isEmpty();
    }

    @Test
    void nobodyNearbyMeansNextBookableSlotsInstead() {
        capacityFor(MON.plusDays(1), 9, 12, 30);

        InstantAvailability result = instant.check("koramangala", ServiceType.CLEANING, 60, Tier.STANDARD);

        assertThat(result.available()).isFalse();
        assertThat(result.alternatives()).extracting(InstantAvailability.Alternative::start)
                .containsExactly(MON.plusDays(1).atTime(9, 30), MON.plusDays(1).atTime(10, 0),
                        MON.plusDays(1).atTime(10, 30));
    }

    @Test
    void noZoneCapacityMeansNotAvailableEvenWithAnIdleMaid() {
        working("m-01", "kor-5", LiveStatus.IDLE, "13:00", ServiceType.CLEANING);
        TimeSlot job = new TimeSlot(MON.atTime(10, 20), MON.atTime(11, 20)).withBufferBefore(Duration.ofMinutes(15));
        capacity.reserve("kor-5", ServiceType.CLEANING, job, "j-a", null);
        capacity.reserve("kor-5", ServiceType.CLEANING, job, "j-b", null);

        assertThat(instant.check("koramangala", ServiceType.CLEANING, 60, Tier.STANDARD).available()).isFalse();
    }
}
