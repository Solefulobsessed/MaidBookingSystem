package com.example.maidbooking.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentRepository;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.allocation.CandidateFinder;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.InvalidFilterException;
import com.example.maidbooking.discovery.filter.StandardFilters;
import com.example.maidbooking.pricing.TestRateCard;
import com.example.maidbooking.pricing.TierPricing;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class MaidSearchServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime TEN = MON.atTime(10, 0);

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final MutableClock clock = new MutableClock(MON.minusDays(1).atTime(18, 0));
    private final ZoneDistanceTravelTimeEstimator travel = new ZoneDistanceTravelTimeEstimator(15, 30);
    private final CapacityService capacity = new CapacityService(buckets, new CapacityReservationRepository(),
            new InMemoryLockRegistry(), event -> { }, clock, new CapacityProperties(1.0));
    private MaidSearchService search;

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of("hsr-1")));
        zones.save(new Zone("hsr-1", "HSR Layout", Set.of("hsr layout"), Set.of()));
        StandardFilters standard = new StandardFilters();
        FilterRegistry registry = new FilterRegistry(List.of(standard.minRatingFilter(), standard.tierFilter(),
                standard.genderFilter(), standard.skillsFilter()));
        search = new MaidSearchService(new LocalityZoneResolver(zones), registry,
                new CandidateFinder(zones, shifts, maids, assignments, travel), capacity,
                new TierPricing(TestRateCard.standard()), travel, clock, 120);
        onShift("m-01", "kor-5", Gender.FEMALE, Tier.PREMIUM, 4.8, "deep-cleaning");
        onShift("m-02", "kor-5", Gender.MALE, Tier.STANDARD, 4.2);
        onShift("m-03", "hsr-1", Gender.FEMALE, Tier.STANDARD, 4.8);
        zoneCapacity(90);
    }

    private void onShift(String id, String zone, Gender gender, Tier tier, double rating, String... tags) {
        Maid maid = new Maid(id, "Maid " + id, gender, "98765432" + id.substring(2), Set.of(ServiceType.CLEANING),
                Set.of(tags), tier, zone, rating);
        maid.activate();
        maids.save(maid);
        shifts.save(new Shift("s-" + id, id, zone, new TimeSlot(MON.atTime(7, 0), MON.atTime(13, 0))));
    }

    private void zoneCapacity(long minutesPerBucket) {
        for (LocalDateTime t = MON.atTime(7, 0); t.isBefore(MON.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(minutesPerBucket);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(minutesPerBucket);
        }
    }

    private MaidSearchResult find(Map<String, String> filters) {
        return search.search("koramangala", ServiceType.CLEANING, TEN, 60, filters, 0, 20);
    }

    @Test
    void freeMaidsSortedByRatingThenPriceWithTheirOwnQuotes() {
        MaidSearchResult result = find(Map.of());

        assertThat(result.items()).extracting(MaidSearchResult.Item::maidId).containsExactly("m-03", "m-01", "m-02");
        assertThat(result.items().get(0).quotedPrice()).as("standard").isEqualTo(Money.of(249));
        assertThat(result.items().get(1).quotedPrice()).as("premium").isEqualTo(Money.of("323.70"));
        assertThat(result.items().get(0).zoneId()).isEqualTo("hsr-1");
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void filtersNarrowTheList() {
        assertThat(find(Map.of("gender", "FEMALE", "skills", "deep-cleaning")).items())
                .extracting(MaidSearchResult.Item::maidId).containsExactly("m-01");
        assertThat(find(Map.of("minRating", "4.5", "tier", "STANDARD")).items())
                .extracting(MaidSearchResult.Item::maidId).containsExactly("m-03");
    }

    @Test
    void busyMaidsAreLeftOut() {
        TimeSlot work = TimeSlot.of(TEN, Duration.ofHours(1));
        assignments.save(new Assignment("a-1", "j-1", "m-01", "s-m-01", "kor-5", work,
                work.withBufferBefore(Duration.ofMinutes(15)), AssignmentStatus.ACTIVE));

        assertThat(find(Map.of()).items()).extracting(MaidSearchResult.Item::maidId).doesNotContain("m-01");
    }

    @Test
    void noZoneCapacityMeansNoMaidsEvenIfSomeoneLooksFree() {
        capacity.reserve("kor-5", ServiceType.CLEANING, TimeSlot.of(TEN, Duration.ofHours(1))
                .withBufferBefore(Duration.ofMinutes(15)), "j-unassigned", null);
        capacity.reserve("kor-5", ServiceType.CLEANING, TimeSlot.of(TEN, Duration.ofHours(1))
                .withBufferBefore(Duration.ofMinutes(15)), "j-unassigned-2", null);
        capacity.reserve("kor-5", ServiceType.CLEANING, TimeSlot.of(TEN, Duration.ofHours(1))
                .withBufferBefore(Duration.ofMinutes(15)), "j-unassigned-3", null);

        assertThat(find(Map.of()).items()).isEmpty();
    }

    @Test
    void paging() {
        MaidSearchResult second = search.search("koramangala", ServiceType.CLEANING, TEN, 60, Map.of(), 1, 1);

        assertThat(second.items()).extracting(MaidSearchResult.Item::maidId).containsExactly("m-01");
        assertThat(second.total()).isEqualTo(3);
        assertThat(search.search("koramangala", ServiceType.CLEANING, TEN, 60, Map.of(), 5, 1).items()).isEmpty();
    }

    @Test
    void validation() {
        assertThatThrownBy(() -> find(Map.of("minRatng", "4"))).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> search.search("koramangala", ServiceType.CLEANING, TEN, 45, Map.of(), 0, 20))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> search.search("koramangala", ServiceType.CLEANING, TEN, 60, Map.of(), 0, 51))
                .isInstanceOf(ValidationException.class);
        clock.set(MON.atTime(9, 0));
        assertThatThrownBy(() -> find(Map.of())).isInstanceOf(ValidationException.class)
                .hasMessageContaining("120 minutes");
    }
}
