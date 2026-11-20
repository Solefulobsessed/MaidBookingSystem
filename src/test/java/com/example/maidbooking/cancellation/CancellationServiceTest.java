package com.example.maidbooking.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.capacity.ReservationStatus;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.discovery.SlotStatus;
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
    private final MockPaymentGateway gateway = new MockPaymentGateway();
    private final AllocationService allocation = mock(AllocationService.class);
    private SlotService slots;
    private CancellationService service;

    @BeforeEach
    void setUp() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        slots = new SlotService(new LocalityZoneResolver(zones), capacity, new TierPricing(TestRateCard.standard()),
                new ZoneDistanceTravelTimeEstimator(15, 30), clock, 120);
        service = new CancellationService(new TimeBasedCancellationPolicy(new CancellationProperties(null, null, null)),
                jobs, payments, new PaymentProcessorRegistry(List.of(new UpiPaymentProcessor(gateway))), allocation,
                capacity, new JobGuard(), clock);
        // two cleaners on shift Monday 07:00-13:00
        for (LocalDateTime t = MON.atTime(7, 0); t.isBefore(MON.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(60);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(60);
        }
    }

    /** A 09:00 job booked with its 15-minute travel buffer; paid and assigned if {@code paid}. */
    private Job job(boolean paid) {
        String reservationId = capacity.reserve("kor-5", ServiceType.CLEANING,
                new TimeSlot(MON.atTime(8, 45), MON.atTime(10, 0)), "j-1", LocalDateTime.now(clock).plusMinutes(10)).id();
        Job job = jobs.save(new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, NINE_TO_TEN, Money.of(249),
                reservationId, "4821", LocalDateTime.now(clock).plusMinutes(10)));
        if (paid) {
            Payment payment = payments.save(new Payment("p-1", "j-1", Money.of(249), PaymentMethodType.UPI, "k-1"));
            payment.succeed(gateway.charge(new GatewayChargeRequest("k-1", Money.of(249), "UPI:user@okbank")).gatewayRef());
            capacity.commit(reservationId);
            job.confirm(payment.id());
            job.assign("m-01", "as-1");
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
        assertThat(payments.getById("p-1").status()).isEqualTo(PaymentStatus.REFUNDED);
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
        assertThat(payments.getById("p-1").status()).isEqualTo(PaymentStatus.SUCCEEDED);
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

        Payment payment = payments.getById("p-1");
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
}
