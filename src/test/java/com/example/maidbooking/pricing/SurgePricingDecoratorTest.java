package com.example.maidbooking.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobReserver;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.SlotGrid;
import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class SurgePricingDecoratorTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot NINE_TO_TEN = new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0));

    private final MutableClock clock = new MutableClock(MON.minusDays(1).atTime(18, 0));
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityService capacity = new CapacityService(buckets, new CapacityReservationRepository(),
            new InMemoryLockRegistry(), event -> { }, clock, new CapacityProperties(1.0));
    private final ZoneRepository zones = new ZoneRepository();
    private final SurgePricingDecorator surge = new SurgePricingDecorator(new TierPricing(TestRateCard.standard()),
            capacity, new SurgeProperties(null, null, null));

    @BeforeEach
    void setUp() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        // ten cleaners' worth: 300 minutes in every 30-minute bucket, 07:00-13:00
        for (LocalDateTime t = MON.atTime(7, 0); t.isBefore(MON.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(300);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(300);
        }
    }

    private void fill(LocalTime from, int jobs) {
        for (int i = 0; i < jobs; i++) {
            capacity.reserve("kor-5", ServiceType.CLEANING, TimeSlot.of(MON.atTime(from), Duration.ofHours(1)),
                    "busy-" + i, null);
        }
    }

    private Money price(boolean instant) {
        return surge.price(new PriceQuery(ServiceType.CLEANING, Tier.STANDARD, NINE_TO_TEN, "kor-5", instant));
    }

    @Test
    void plentyOfRoomMeansTheRateCardPrice() {
        fill(LocalTime.of(9, 0), 7);  // 30% left

        assertThat(price(false)).isEqualTo(Money.of(249));
    }

    @Test
    void underTwentyPercentLeftAddsTwentyPercent() {
        fill(LocalTime.of(9, 0), 9);  // 10% left

        assertThat(price(false)).isEqualTo(Money.of("298.80"));
    }

    @Test
    void exactlyTwentyPercentLeftIsNotSurge() {
        fill(LocalTime.of(9, 0), 8);

        assertThat(price(false)).isEqualTo(Money.of(249));
    }

    @Test
    void theTightestBucketOfTheSlotDecides() {
        fill(LocalTime.of(9, 30), 9);  // only the second half-hour is nearly full; 09:30-10:30 jobs

        assertThat(price(false)).isEqualTo(Money.of("298.80"));
    }

    @Test
    void instantJobsAddAFixedPremiumOnTopOfAnySurge() {
        assertThat(price(true)).isEqualTo(Money.of(298));
        fill(LocalTime.of(9, 0), 9);
        assertThat(price(true)).isEqualTo(Money.of("347.80"));
    }

    @Test
    void theQuotedSlotPriceIsWhatTheJobIsCharged() {
        fill(LocalTime.of(9, 0), 9);
        SlotService slots = new SlotService(new LocalityZoneResolver(zones), capacity, surge,
                new ZoneDistanceTravelTimeEstimator(15, 30), clock, 120);
        JobReserver reserver = new JobReserver(zones, new ZoneDistanceTravelTimeEstimator(15, 30), surge, capacity);

        SlotGrid.Slot quoted = slots.slots("koramangala", ServiceType.CLEANING, MON, 60, Tier.STANDARD).slots().stream()
                .filter(slot -> slot.start().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        Job job = reserver.reserve("b-1", "kor-5", ServiceType.CLEANING, Tier.STANDARD, NINE_TO_TEN, null, false);

        assertThat(quoted.price()).isEqualTo(Money.of("298.80"));
        assertThat(job.price()).isEqualTo(quoted.price());
    }

    @Test
    void settingsAreChecked() {
        assertThatThrownBy(() -> new SurgeProperties(1.5, 20, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SurgePricingDecorator(new TierPricing(TestRateCard.standard()), capacity,
                new SurgeProperties(0.0, 0, BigDecimal.ZERO))
                .price(new PriceQuery(ServiceType.CLEANING, Tier.STANDARD, NINE_TO_TEN, "kor-5", true)))
                .as("surge switched off").isEqualTo(Money.of(249));
    }
}
