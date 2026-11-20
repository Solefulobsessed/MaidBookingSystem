package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.type.AllocationMode;
import com.example.maidbooking.booking.type.BookingPlan;
import com.example.maidbooking.booking.type.BookingTypeHandler;
import com.example.maidbooking.booking.type.BookingTypeRegistry;
import com.example.maidbooking.booking.type.RecurringBookingHandler;
import com.example.maidbooking.booking.type.ScheduledBookingHandler;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservation;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.capacity.ReservationStatus;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.NotFoundException;
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
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class BookingServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime NOW = MON.minusDays(1).atTime(18, 0);

    private final MutableClock clock = new MutableClock(NOW);
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityReservationRepository reservations = new CapacityReservationRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final JobRepository jobs = new JobRepository();
    private final AllocationService allocation = mock(AllocationService.class);
    private final MaidRepository maids = new MaidRepository();
    private final SubscriptionRepository subscriptions = new SubscriptionRepository();
    private BookingService service;

    /** Dispatches at once, like instant bookings, for a fixed 09:00-10:00 job. */
    private static final class DispatchNowHandler implements BookingTypeHandler {
        @Override public BookingType type() { return BookingType.INSTANT; }
        @Override public void validate(CreateBookingRequest request, LocalDateTime now) { }
        @Override public AllocationMode allocationMode() { return AllocationMode.DISPATCH_NOW; }
        @Override public BookingPlan plan(CreateBookingRequest request, LocalDateTime now) {
            return BookingPlan.single(new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0)));
        }
    }

    @BeforeEach
    void setUp() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        StandardFilters standard = new StandardFilters();
        LocalityZoneResolver resolver = new LocalityZoneResolver(zones);
        CapacityService capacity = new CapacityService(buckets, reservations, new InMemoryLockRegistry(), event -> { },
                clock, new CapacityProperties(1.0));
        service = new BookingService(
                new BookingTypeRegistry(List.of(new ScheduledBookingHandler(120, 30),
                        new RecurringBookingHandler(resolver, 120, 30), new DispatchNowHandler())),
                resolver, new ZoneDistanceTravelTimeEstimator(15, 30),
                new FilterRegistry(List.of(standard.genderFilter(), standard.minRatingFilter())),
                new TierPricing(TestRateCard.standard()), capacity, allocation, maids, subscriptions, bookings, jobs, clock, 10);
        maids.save(new Maid("m-7f3a", "Lakshmi R", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING), null,
                Tier.STANDARD, "kor-5", 4.6));
        // one cleaner on shift 07:00-13:00 every day for the next two weeks
        for (LocalDate day = MON; day.isBefore(MON.plusDays(14)); day = day.plusDays(1)) {
            for (LocalDateTime t = day.atTime(7, 0); t.isBefore(day.atTime(13, 0)); t = t.plusMinutes(30)) {
                buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(30);
                buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(30);
            }
        }
    }

    private static CreateBookingRequest request(BookingType type, Map<String, String> preferences) {
        return preferring(type, preferences, null, Tier.STANDARD);
    }

    private static CreateBookingRequest preferring(BookingType type, Map<String, String> preferences, String maidId,
                                                   Tier tier) {
        return new CreateBookingRequest(type, "c-1", "Koramangala", "Flat 302", ServiceType.CLEANING, tier,
                MON.atTime(9, 0), 60, preferences, maidId, null);
    }

    private static CreateBookingRequest weekdays(String preferredMaidId) {
        RecurrenceRule rule = new RecurrenceRule(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), Duration.ofMinutes(60), MON,
                MON.plusMonths(3));
        return new CreateBookingRequest(BookingType.RECURRING, "c-1", "Koramangala", "Flat 302", ServiceType.CLEANING,
                Tier.STANDARD, null, null, Map.of(), preferredMaidId, rule);
    }

    private long reservedMinutes(int hour, int minute) {
        return buckets.getOrCreate(BucketKey.forZone("kor-5", MON.atTime(hour, minute))).reservedMinutes();
    }

    @Test
    void scheduledBookingCreatesOneUnpaidJobWithHeldCapacity() {
        BookingDetails created = service.create(request(BookingType.SCHEDULED, Map.of("gender", "FEMALE")));

        assertThat(created.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(created.amountDueNow()).isEqualTo(Money.of(249));
        assertThat(created.jobs()).singleElement().satisfies(job -> {
            assertThat(job.slot()).isEqualTo(new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0)));
            assertThat(job.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
            assertThat(job.price().amount()).isEqualByComparingTo(new BigDecimal("249"));
            assertThat(job.holdExpiresAt()).isEqualTo(NOW.plusMinutes(10));
            assertThat(job.otp()).matches("\\d{4}");
            CapacityReservation reservation = reservations.getById(job.reservationId());
            assertThat(reservation.status()).isEqualTo(ReservationStatus.HELD);
            assertThat(reservation.referenceId()).isEqualTo(job.id());
            assertThat(reservation.expiresAt()).isEqualTo(NOW.plusMinutes(10));
        });
        assertThat(reservedMinutes(8, 30)).as("travel buffer").isEqualTo(15);
        assertThat(reservedMinutes(9, 0)).isEqualTo(30);

        Booking booking = created.booking();
        assertThat(booking.zoneId()).isEqualTo("kor-5");
        assertThat(booking.preferredMaidId()).isNull();
        assertThat(booking.constraints().describe()).contains("FEMALE");
        assertThat(booking.jobIds()).containsExactly(created.jobs().get(0).id());
        assertThat(service.get(booking.id()).jobs()).extracting(Job::id).isEqualTo(booking.jobIds());
        assertThat(service.forCustomer("c-1")).extracting(details -> details.booking().id()).containsExactly(booking.id());
        verifyNoInteractions(allocation);
    }

    @Test
    void recurringBookingReservesTwoWeeksOfVisitsAndSavesTheSubscription() {
        BookingDetails created = service.create(weekdays("m-7f3a"));

        assertThat(created.jobs()).hasSize(10).allSatisfy(job -> assertThat(job.slot().start().toLocalTime())
                .isEqualTo(LocalTime.of(9, 0)));
        assertThat(created.jobs()).extracting(job -> job.slot().start().toLocalDate())
                .doesNotContain(MON.plusDays(5), MON.plusDays(6)).contains(MON, MON.plusDays(11));
        assertThat(created.jobs().get(0).holdExpiresAt()).as("paid now").isEqualTo(NOW.plusMinutes(10));
        assertThat(created.jobs().subList(1, 10)).allSatisfy(job -> {
            assertThat(job.holdExpiresAt()).as("charged later").isNull();
            assertThat(reservations.getById(job.reservationId()).expiresAt()).isNull();
        });
        assertThat(created.amountDueNow()).isEqualTo(Money.of(249));

        Subscription subscription = subscriptions.getById(created.booking().subscriptionId());
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.generatedUntil()).isEqualTo(MON.plusDays(13));
        assertThat(subscription.preferredMaidId()).isEqualTo("m-7f3a");
        verifyNoInteractions(allocation);  // the preferred maid is only a preference for a series
    }

    @Test
    void oneClashingVisitFailsTheWholeSeriesAndNamesTheDate() {
        LocalDate wednesday = MON.plusDays(2);
        service.create(new CreateBookingRequest(BookingType.SCHEDULED, "c-2", "Koramangala", "Flat 9",
                ServiceType.CLEANING, Tier.STANDARD, wednesday.atTime(9, 0), 60, Map.of(), null, null));

        assertThatThrownBy(() -> service.create(weekdays(null)))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
                    assertThat(e.details().get("conflictDates")).isEqualTo(List.of(wednesday.toString()));
                    assertThat(e.getMessage()).contains(wednesday.toString());
                });

        assertThat(reservedMinutes(9, 0)).as("Monday's visit was released").isZero();
        assertThat(reservations.findAll()).filteredOn(r -> r.status() != ReservationStatus.RELEASED)
                .singleElement().satisfies(r -> assertThat(r.blocked().start()).isEqualTo(wednesday.atTime(8, 45)));
        assertThat(bookings.findAll()).hasSize(1);
        assertThat(subscriptions.findAll()).isEmpty();
    }

    @Test
    void anUnknownPreferenceIsRejectedBeforeAnythingIsReserved() {
        assertThatThrownBy(() -> service.create(request(BookingType.SCHEDULED, Map.of("hairColour", "black"))))
                .isInstanceOf(InvalidFilterException.class);

        assertThat(reservations.findAll()).isEmpty();
        assertThat(bookings.findAll()).isEmpty();
    }

    @Test
    void anInvalidStartIsRejectedBeforeAnythingIsReserved() {
        clock.set(MON.atTime(8, 0));

        assertThatThrownBy(() -> service.create(request(BookingType.SCHEDULED, Map.of())))
                .hasMessageContaining("at least 120 minutes");
        assertThat(reservations.findAll()).isEmpty();
    }

    @Test
    void dispatchNowHoldsTheMaidInTheSameRequest() {
        given(allocation.dispatch(any(), any())).willAnswer(call -> {
            String jobId = call.<AllocationRequest>getArgument(0).referenceId();
            TimeSlot work = new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0));
            return Optional.of(new Assignment("as-1", jobId, "m-01", "s-1", "kor-5", work, work, AssignmentStatus.HELD));
        });

        BookingDetails created = service.create(request(BookingType.INSTANT, Map.of()));

        Job job = created.jobs().get(0);
        assertThat(job.maidId()).isEqualTo("m-01");
        assertThat(job.assignmentId()).isEqualTo("as-1");
        assertThat(job.status()).as("the maid waits for payment").isEqualTo(JobStatus.PENDING_PAYMENT);
        assertThat(reservations.getById(job.reservationId()).status()).isEqualTo(ReservationStatus.HELD);
        assertThat(jobs.findAll()).containsExactly(job);
    }

    @Test
    void noMaidNearbyReleasesTheCapacityAndSavesNothing() {
        given(allocation.dispatch(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(BookingType.INSTANT, Map.of())))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_MAID_NEARBY));

        assertThat(reservedMinutes(9, 0)).isZero();
        assertThat(reservations.findAll()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(ReservationStatus.RELEASED));
        verify(allocation).release(reservations.findAll().get(0).referenceId());
        assertThat(bookings.findAll()).isEmpty();
        assertThat(jobs.findAll()).isEmpty();
    }

    private static Assignment heldFor(String jobId, String maidId) {
        TimeSlot work = new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0));
        return new Assignment("as-1", jobId, maidId, "s-1", "kor-5", work, work, AssignmentStatus.HELD);
    }

    @Test
    void preferredMaidIsHeldWithTheBooking() {
        given(allocation.allocateOnly(eq("m-7f3a"), any(), eq(AssignmentStatus.HELD))).willAnswer(call ->
                Optional.of(heldFor(call.<AllocationRequest>getArgument(1).referenceId(), "m-7f3a")));

        BookingDetails created = service.create(preferring(BookingType.SCHEDULED, Map.of(), "m-7f3a", Tier.STANDARD));

        assertThat(created.booking().preferredMaidId()).isEqualTo("m-7f3a");
        assertThat(created.jobs().get(0).maidId()).isEqualTo("m-7f3a");
        assertThat(created.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(allocation).allocateOnly(eq("m-7f3a"), argThat(r -> "m-7f3a".equals(r.preferredMaidId())),
                eq(AssignmentStatus.HELD));
    }

    @Test
    void busyPreferredMaidIs409AndNothingStaysHeld() {
        given(allocation.allocateOnly(any(), any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(preferring(BookingType.SCHEDULED, Map.of(), "m-7f3a", Tier.STANDARD)))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MAID_UNAVAILABLE);
                    assertThat(e.details()).containsEntry("maidId", "m-7f3a");
                });

        assertThat(reservedMinutes(9, 0)).isZero();
        assertThat(reservations.findAll()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(ReservationStatus.RELEASED));
        verify(allocation).release(reservations.findAll().get(0).referenceId());
        assertThat(bookings.findAll()).isEmpty();
        assertThat(jobs.findAll()).isEmpty();
    }

    @Test
    void preferredMaidOfAnotherTierOrUnknownIsRejectedBeforeReserving() {
        assertThatThrownBy(() -> service.create(preferring(BookingType.SCHEDULED, Map.of(), "m-7f3a", Tier.PREMIUM)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("PREMIUM");
        assertThatThrownBy(() -> service.create(preferring(BookingType.SCHEDULED, Map.of(), "m-nobody", Tier.STANDARD)))
                .isInstanceOf(NotFoundException.class);

        assertThat(reservations.findAll()).isEmpty();
        verifyNoInteractions(allocation);
    }
}
