package com.example.maidbooking.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.example.maidbooking.pricing.TierPricing;
import com.example.maidbooking.pricing.TestRateCard;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class SlotServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final MutableClock clock = new MutableClock(MON.minusDays(1).atTime(18, 0));
    private final CapacityService capacity = new CapacityService(buckets, new CapacityReservationRepository(),
            new InMemoryLockRegistry(), event -> { }, clock, new CapacityProperties(1.0));
    private SlotService slotService;

    @BeforeEach
    void setUp() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        slotService = new SlotService(new LocalityZoneResolver(zones), capacity, new TierPricing(TestRateCard.standard()),
                new ZoneDistanceTravelTimeEstimator(15, 30), clock, 120);
        // two cleaners on shift Monday 07:00-13:00
        for (LocalDateTime t = MON.atTime(7, 0); t.isBefore(MON.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(60);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(60);
        }
    }

    private Map<LocalTime, SlotGrid.Slot> grid(int minutes) {
        SlotGrid grid = slotService.slots("Koramangala", ServiceType.CLEANING, MON, minutes, Tier.STANDARD);
        assertThat(grid.zoneId()).isEqualTo("kor-5");
        return grid.slots().stream().collect(Collectors.toMap(SlotGrid.Slot::start, Function.identity()));
    }

    @Test
    void statusFollowsFreeCapacityIncludingTravel() {
        Map<LocalTime, SlotGrid.Slot> slots = grid(60);

        assertThat(slots.get(LocalTime.of(7, 0)).status()).as("needs 06:45 travel, before the shift").isEqualTo(SlotStatus.FULL);
        assertThat(slots.get(LocalTime.of(7, 30)).status()).isEqualTo(SlotStatus.OPEN);
        assertThat(slots.get(LocalTime.of(12, 0)).status()).isEqualTo(SlotStatus.OPEN);
        assertThat(slots.get(LocalTime.of(12, 30)).status()).as("runs past the shift").isEqualTo(SlotStatus.FULL);
        assertThat(slots.get(LocalTime.of(20, 0)).status()).isEqualTo(SlotStatus.FULL);
        assertThat(slots).hasSize(27);
    }

    @Test
    void bookingOneOfTwoMaidsLeavesFewLeft() {
        capacity.reserve("kor-5", ServiceType.CLEANING,
                TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1)).withBufferBefore(Duration.ofMinutes(15)), "j-1", null);

        Map<LocalTime, SlotGrid.Slot> slots = grid(60);

        assertThat(slots.get(LocalTime.of(9, 0)).status()).isEqualTo(SlotStatus.FEW_LEFT);
        assertThat(slots.get(LocalTime.of(8, 30)).status()).as("overlaps the booked hour").isEqualTo(SlotStatus.FEW_LEFT);
        assertThat(slots.get(LocalTime.of(11, 0)).status()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void openSlotsArePricedFullOnesAreNot() {
        Map<LocalTime, SlotGrid.Slot> slots = grid(90);

        assertThat(slots.get(LocalTime.of(8, 0)).price()).isEqualTo(Money.of("373.50"));
        assertThat(slots.get(LocalTime.of(7, 0)).price()).isNull();
    }

    @Test
    void startsInsideTheLeadTimeOrPastMidnightAreLeftOut() {
        clock.set(MON.atTime(8, 0));
        Map<LocalTime, SlotGrid.Slot> slots = grid(240);

        assertThat(slots.keySet()).allSatisfy(start -> assertThat(start).isAfterOrEqualTo(LocalTime.of(10, 0)));
        assertThat(slots).as("a 4-hour job from 20:00 would end at midnight").containsKey(LocalTime.of(19, 30))
                .doesNotContainKey(LocalTime.of(20, 0)).hasSize(20);
    }

    @Test
    void durationAndLocalityAreValidated() {
        assertThatThrownBy(() -> grid(45)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> grid(270)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> slotService.slots("Whitefield", ServiceType.CLEANING, MON, 60, Tier.STANDARD))
                .isInstanceOf(ValidationException.class);
    }
}
