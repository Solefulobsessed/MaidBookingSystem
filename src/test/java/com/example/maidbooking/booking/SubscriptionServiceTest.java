package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.pricing.TestRateCard;
import com.example.maidbooking.pricing.TierPricing;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class SubscriptionServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDate NEXT_DAY = MON.plusDays(14);  // Mon 7 Dec, the day the run adds

    private final MutableClock clock = new MutableClock(MON.plusDays(1).atTime(0, 30));
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final SubscriptionRepository subscriptions = new SubscriptionRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final JobRepository jobs = new JobRepository();
    private final AllocationService allocation = mock(AllocationService.class);
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        CapacityService capacity = new CapacityService(buckets, new CapacityReservationRepository(),
                new InMemoryLockRegistry(), event -> { }, clock, new CapacityProperties(1.0));
        JobReserver reserver = new JobReserver(zones, new ZoneDistanceTravelTimeEstimator(15, 30),
                new TierPricing(TestRateCard.standard()), capacity);
        service = new SubscriptionService(subscriptions, bookings, jobs, reserver, allocation, clock, 120);
        given(allocation.allocate(any(), eq(AssignmentStatus.HELD))).willAnswer(call -> {
            AllocationRequest request = call.getArgument(0);
            String maid = request.preferredMaidId() == null ? "m-any" : request.preferredMaidId();
            return Optional.of(new Assignment("as-" + request.referenceId(), request.referenceId(), maid, "s-1",
                    "kor-5", request.work(), request.work(), AssignmentStatus.HELD));
        });
    }

    private void capacityOn(LocalDate day) {
        for (LocalDateTime t = day.atTime(7, 0); t.isBefore(day.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(30);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(30);
        }
    }

    /** A weekday series booked on Sunday 22 Nov with jobs up to Sun 6 Dec; one existing visit has {@code maidId}. */
    private Subscription series(String id, String preferredMaidId, String maidId) {
        Subscription subscription = new Subscription(id, "c-" + id, "kor-5", ServiceType.CLEANING,
                new RecurrenceRule(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), Duration.ofHours(1), MON,
                        MON.plusMonths(3)), Tier.STANDARD, preferredMaidId, MON.plusDays(13));
        Booking booking = new Booking("b-" + id, "c-" + id, "kor-5", "Flat 302", ServiceType.CLEANING,
                BookingType.RECURRING, Tier.STANDARD, MaidFilter.all(List.of()), preferredMaidId, id,
                MON.minusDays(1).atTime(18, 0));
        Job lastVisit = new Job("j-" + id + "-last", booking.id(), "kor-5", ServiceType.CLEANING,
                TimeSlot.of(MON.plusDays(11).atTime(9, 0), Duration.ofHours(1)), Money.of(249), "cr-x", "1234", null);
        if (maidId != null) {
            lastVisit.holdMaid(maidId, "as-x");
        }
        booking.addJob(lastVisit.id());
        jobs.save(lastVisit);
        bookings.save(booking);
        return subscriptions.save(subscription);
    }

    @Test
    void addsTheNextDayAndOffersItToTheMaidOfTheLatestVisit() {
        capacityOn(NEXT_DAY);
        Subscription subscription = series("sub-1", "m-pref", "m-usual");

        SubscriptionGenerationResult result = service.generate();

        assertThat(result).isEqualTo(new SubscriptionGenerationResult(1, 0));
        Job added = jobs.findByBooking("b-sub-1").get(1);
        assertThat(added.slot()).isEqualTo(TimeSlot.of(NEXT_DAY.atTime(9, 0), Duration.ofHours(1)));
        assertThat(added.maidId()).isEqualTo("m-usual");
        assertThat(added.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        assertThat(added.holdExpiresAt()).as("charged a day before, not held for 10 minutes").isNull();
        assertThat(bookings.getById("b-sub-1").jobIds()).endsWith(added.id());
        assertThat(subscription.generatedUntil()).isEqualTo(NEXT_DAY);
    }

    @Test
    void withoutAnEarlierMaidThePreferredMaidIsTriedFirst() {
        capacityOn(NEXT_DAY);
        series("sub-1", "m-pref", null);

        service.generate();

        ArgumentCaptor<AllocationRequest> asked = ArgumentCaptor.forClass(AllocationRequest.class);
        verify(allocation).allocate(asked.capture(), eq(AssignmentStatus.HELD));
        assertThat(asked.getValue().preferredMaidId()).isEqualTo("m-pref");
        assertThat(jobs.findByBooking("b-sub-1").get(1).maidId()).isEqualTo("m-pref");
    }

    @Test
    void aDayWithoutCapacityIsSkippedAndTheSeriesCarriesOn() {
        Subscription subscription = series("sub-1", null, null);

        SubscriptionGenerationResult result = service.generate();

        assertThat(result).isEqualTo(new SubscriptionGenerationResult(0, 1));
        assertThat(subscription.skippedNoCapacity()).containsExactly(NEXT_DAY);
        assertThat(subscription.generatedUntil()).isEqualTo(NEXT_DAY);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(jobs.findByBooking("b-sub-1")).hasSize(1);

        capacityOn(NEXT_DAY.plusDays(1));
        clock.advance(Duration.ofDays(1));
        assertThat(service.generate()).as("next day works again").isEqualTo(new SubscriptionGenerationResult(1, 0));
    }

    @Test
    void runningTwiceOnTheSameDayAddsNothing() {
        capacityOn(NEXT_DAY);
        series("sub-1", null, null);

        service.generate();

        assertThat(service.generate()).isEqualTo(new SubscriptionGenerationResult(0, 0));
        assertThat(jobs.findByBooking("b-sub-1")).hasSize(2);
    }

    @Test
    void pausedSubscriptionsAreLeftAlone() {
        capacityOn(NEXT_DAY);
        Subscription paused = series("sub-1", null, null);
        paused.pause();

        assertThat(service.generate()).isEqualTo(new SubscriptionGenerationResult(0, 0));
        assertThat(paused.generatedUntil()).isEqualTo(MON.plusDays(13));
        verifyNoInteractions(allocation);
    }

    @Test
    void weekendDaysExtendTheWindowWithoutVisits() {
        Subscription subscription = series("sub-1", null, null);
        clock.set(MON.minusDays(1).atTime(0, 30));  // Sunday 22 Nov: the window reaches Sat 5 Dec
        Subscription upToFriday = subscriptions.save(new Subscription("sub-2", "c-2", "kor-5", ServiceType.CLEANING,
                subscription.rule(), Tier.STANDARD, null, MON.plusDays(11)));
        bookings.save(new Booking("b-sub-2", "c-2", "kor-5", "Flat 9", ServiceType.CLEANING, BookingType.RECURRING,
                Tier.STANDARD, MaidFilter.all(List.of()), null, "sub-2", MON.minusDays(2).atTime(9, 0)));

        assertThat(service.generate()).isEqualTo(new SubscriptionGenerationResult(0, 0));
        assertThat(upToFriday.generatedUntil()).isEqualTo(MON.plusDays(12));
        assertThat(subscription.generatedUntil()).as("already ahead of the window").isEqualTo(MON.plusDays(13));
    }
}
