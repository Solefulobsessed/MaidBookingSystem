package com.example.maidbooking.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobReserver;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.booking.Subscription;
import com.example.maidbooking.booking.SubscriptionGenerationResult;
import com.example.maidbooking.booking.SubscriptionRepository;
import com.example.maidbooking.booking.SubscriptionService;
import com.example.maidbooking.booking.SubscriptionStatus;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.capacity.ReservationStatus;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.discovery.SlotStatus;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.payment.GatewayChargeRequest;
import com.example.maidbooking.payment.GatewayUnavailableException;
import com.example.maidbooking.payment.MockPaymentGateway;
import com.example.maidbooking.payment.Payment;
import com.example.maidbooking.payment.PaymentMethodType;
import com.example.maidbooking.payment.PaymentProcessorRegistry;
import com.example.maidbooking.payment.PaymentRepository;
import com.example.maidbooking.payment.PaymentStatus;
import com.example.maidbooking.payment.UpiPaymentProcessor;
import com.example.maidbooking.pricing.TestRateCard;
import com.example.maidbooking.pricing.TierPricing;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class CancellationServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot NINE_TO_TEN = new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0));

    private final MutableClock clock = new MutableClock(MON.minusDays(1).atTime(18, 0));
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityReservationRepository reservations = new CapacityReservationRepository();
    private final CapacityService capacity = new CapacityService(buckets, reservations, new InMemoryLockRegistry(),
            event -> { }, clock, new CapacityProperties(1.0));
    private final JobRepository jobs = new JobRepository();
    private final PaymentRepository payments = new PaymentRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final SubscriptionRepository subscriptions = new SubscriptionRepository();
    private final ZoneRepository zones = new ZoneRepository();
    private final MockPaymentGateway gateway = new MockPaymentGateway();
    private final AllocationService allocation = mock(AllocationService.class);
    private SlotService slots;
    private CancellationService service;

    @BeforeEach
    void setUp() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        slots = new SlotService(new LocalityZoneResolver(zones), capacity, new TierPricing(TestRateCard.standard()),
                new ZoneDistanceTravelTimeEstimator(15, 30), clock, 120);
        service = new CancellationService(new TimeBasedCancellationPolicy(new CancellationProperties(null, null, null)),
                jobs, bookings, subscriptions, payments, new PaymentProcessorRegistry(List.of(new UpiPaymentProcessor(gateway))), allocation,
                capacity, new JobGuard(), clock);
        // two cleaners on shift Monday and Tuesday 07:00-13:00
        for (LocalDate day : List.of(MON, MON.plusDays(1))) {
            for (LocalDateTime t = day.atTime(7, 0); t.isBefore(day.atTime(13, 0)); t = t.plusMinutes(30)) {
                buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(60);
                buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(60);
            }
        }
    }

    /** A 09:00 job booked with its 15-minute travel buffer; paid and assigned if {@code paid}. */
    private Job job(boolean paid) {
        return job("j-1", MON, paid, LocalDateTime.now(clock).plusMinutes(10));
    }

    private Job job(String id, LocalDate day, boolean paid, LocalDateTime holdUntil) {
        String reservationId = capacity.reserve("kor-5", ServiceType.CLEANING,
                new TimeSlot(day.atTime(8, 45), day.atTime(10, 0)), id, holdUntil).id();
        Job job = jobs.save(new Job(id, "b-1", "kor-5", ServiceType.CLEANING,
                new TimeSlot(day.atTime(9, 0), day.atTime(10, 0)), Money.of(249), reservationId, "4821", holdUntil));
        if (paid) {
            Payment payment = payments.save(new Payment("p-" + id, id, Money.of(249), PaymentMethodType.UPI, "k-" + id));
            payment.succeed(gateway.charge(new GatewayChargeRequest("k-" + id, Money.of(249), "UPI:user@okbank"))
                    .gatewayRef());
            capacity.commit(reservationId);
            job.confirm(payment.id());
            job.assign("m-01", "as-" + id);
        }
        return job;
    }

    private SlotStatus nineOClock() {
        return slots.slots("koramangala", ServiceType.CLEANING, MON, 60, Tier.STANDARD).slots().stream()
                .filter(slot -> slot.start().equals(LocalTime.of(9, 0))).findFirst().orElseThrow().status();
    }

    @Test
    void cancellingRefundsFreesTheMaidAndOpensTheSlotAgain() {
        Job job = job(true);
        assertThat(nineOClock()).isEqualTo(SlotStatus.FEW_LEFT);

        CancellationResult result = service.cancelJob("j-1", CancelledBy.CUSTOMER);

        assertThat(result.jobStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(result.refund().amount()).isEqualTo(Money.of(249));
        assertThat(payments.getById("p-j-1").status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(gateway.moneyMovements()).as("charge + refund").isEqualTo(2);
        assertThat(reservations.getById(job.reservationId()).status()).isEqualTo(ReservationStatus.RELEASED);
        verify(allocation).release("j-1");
        assertThat(nineOClock()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void aFailedRefundChangesNothing() {
        Job job = job(true);
        gateway.declineNext("Refund window closed");

        assertThatThrownBy(() -> service.cancelJob("j-1", CancelledBy.CUSTOMER))
                .isInstanceOfSatisfying(GatewayUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.GATEWAY_ERROR));

        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(payments.getById("p-j-1").status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reservations.getById(job.reservationId()).status()).isEqualTo(ReservationStatus.COMMITTED);
        verify(allocation, never()).release(any());
        assertThat(nineOClock()).isEqualTo(SlotStatus.FEW_LEFT);

        gateway.failNext();
        assertThatThrownBy(() -> service.cancelJob("j-1", CancelledBy.CUSTOMER))
                .as("an outage changes nothing either").isInstanceOf(GatewayUnavailableException.class);
        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
    }

    @Test
    void aLateCancellationGetsHalfBack() {
        job(true);
        clock.set(MON.atTime(6, 0));

        service.cancelJob("j-1", CancelledBy.CUSTOMER);

        Payment payment = payments.getById("p-j-1");
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundedAmount()).isEqualTo(Money.of("124.50"));
    }

    @Test
    void aVeryLateCancellationRefundsNothingButStillFreesTheSlot() {
        job(true);
        clock.set(MON.atTime(8, 30));

        CancellationResult result = service.cancelJob("j-1", CancelledBy.CUSTOMER);

        assertThat(result.refund().amount()).isEqualTo(Money.ZERO);
        assertThat(gateway.moneyMovements()).as("no refund call").isEqualTo(1);
        assertThat(result.jobStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void anUnpaidJobWhoseHoldAlreadyLapsedCanStillBeCancelled() {
        Job job = job(false);
        clock.set(LocalDateTime.now(clock).plusMinutes(30));
        capacity.releaseExpiredHolds();

        CancellationResult result = service.cancelJob("j-1", CancelledBy.CUSTOMER);

        assertThat(result.jobStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(result.refund().reason()).isEqualTo("Nothing was paid");
        assertThat(reservations.getById(job.reservationId()).status()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void aJobWhoseMaidIsOnTheWayIsNotTouched() {
        Job job = job(true);
        job.enRoute();

        assertThatThrownBy(() -> service.cancelJob("j-1", CancelledBy.CUSTOMER))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(job.status()).isEqualTo(JobStatus.EN_ROUTE);
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }

    @Test
    void aQuoteShowsTheRefundAndChangesNothing() {
        Job job = job(true);
        clock.set(MON.atTime(7, 0));

        RefundDecision quote = service.quote("j-1");

        assertThat(quote.percent()).isEqualTo(50);
        assertThat(quote.amount()).isEqualTo(Money.of("124.50"));
        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }

    @Test
    void platformCausedCancellationsRefundInFull() {
        job(true);
        clock.set(MON.atTime(8, 50));

        assertThat(service.cancelJob("j-1", CancelledBy.PLATFORM).refund().amount()).isEqualTo(Money.of(249));
    }

    private Subscription weeklySeries() {
        Subscription subscription = subscriptions.save(new Subscription("sub-1", "c-1", "kor-5", ServiceType.CLEANING,
                new RecurrenceRule(Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(9, 0),
                        Duration.ofHours(1), MON.minusDays(3), MON.plusMonths(2)), Tier.STANDARD, null,
                MON.plusDays(1)));
        bookings.save(new Booking("b-1", "c-1", "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.RECURRING,
                Tier.STANDARD, MaidFilter.all(List.of()), null, "sub-1", MON.minusDays(4).atTime(9, 0)));
        return subscription;
    }

    @Test
    void cancellingASeriesCancelsFutureVisitsAndStopsGeneration() {
        Subscription subscription = weeklySeries();
        Job done = jobs.save(new Job("j-fri", "b-1", "kor-5", ServiceType.CLEANING,
                new TimeSlot(MON.minusDays(3).atTime(9, 0), MON.minusDays(3).atTime(10, 0)), Money.of(249), "cr-old",
                "1111", null));
        done.confirm("p-old");
        done.assign("m-01", "as-old");
        done.enRoute();
        done.start("1111");
        done.complete();
        Job monday = job("j-mon", MON, true, null);
        Job tuesday = job("j-tue", MON.plusDays(1), false, null);

        BookingCancellationResult result = service.cancelBooking("b-1");

        assertThat(result.cancelledJobs()).extracting(CancellationResult::jobId).containsExactly("j-mon", "j-tue");
        assertThat(result.totalRefund()).as("Monday was paid, Tuesday not yet").isEqualTo(Money.of(249));
        assertThat(monday.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(tuesday.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(done.status()).as("past visit untouched").isEqualTo(JobStatus.COMPLETED);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELLED);

        clock.set(MON.atTime(0, 30));
        SubscriptionService generation = new SubscriptionService(subscriptions, bookings, jobs,
                new JobReserver(zones, new ZoneDistanceTravelTimeEstimator(15, 30),
                        new TierPricing(TestRateCard.standard()), capacity), allocation, clock, 120);
        assertThat(generation.generate()).isEqualTo(new SubscriptionGenerationResult(0, 0));
        assertThat(subscription.generatedUntil()).isEqualTo(MON.plusDays(1));
    }

    @Test
    void aSeriesCancellationThatStoppedOnAFailedRefundCanBeRepeated() {
        Subscription subscription = weeklySeries();
        Job monday = job("j-mon", MON, true, null);
        Job tuesday = job("j-tue", MON.plusDays(1), true, null);
        gateway.declineNext("Refund window closed");

        assertThatThrownBy(() -> service.cancelBooking("b-1")).isInstanceOf(GatewayUnavailableException.class);
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(monday.status()).isEqualTo(JobStatus.ASSIGNED);

        BookingCancellationResult retry = service.cancelBooking("b-1");

        assertThat(retry.cancelledJobs()).hasSize(2);
        assertThat(monday.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(tuesday.status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void aOneOffBookingCancelsItsJob() {
        bookings.save(new Booking("b-1", "c-1", "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), null, null, MON.minusDays(2).atTime(9, 0)));
        job(true);

        assertThat(service.cancelBooking("b-1").totalRefund()).isEqualTo(Money.of(249));
        assertThat(jobs.getById("j-1").status()).isEqualTo(JobStatus.CANCELLED);
    }
}
