package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
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
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class PaymentServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime NOW = MON.minusDays(1).atTime(18, 0);
    private static final UpiDetails GOOD_UPI = new UpiDetails("user@okbank");
    private static final TimeSlot NINE_TO_TEN = new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0));

    private final MutableClock clock = new MutableClock(NOW);
    private final CapacityReservationRepository reservations = new CapacityReservationRepository();
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityService capacity = new CapacityService(buckets, reservations, new InMemoryLockRegistry(),
            event -> { }, clock, new CapacityProperties(1.0));
    private final JobRepository jobs = new JobRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final MaidRepository maids = new MaidRepository();
    private final PaymentRepository payments = new PaymentRepository();
    private final AllocationService allocation = mock(AllocationService.class);
    private MockPaymentGateway gateway = new MockPaymentGateway();
    private PaymentService service;
    private Job job;

    @BeforeEach
    void setUp() {
        for (LocalDateTime bucket : List.of(MON.atTime(9, 0), MON.atTime(9, 30))) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, bucket)).addCapacity(30);
            buckets.getOrCreate(BucketKey.forZone("kor-5", bucket)).addCapacity(30);
        }
        bookings.save(new Booking("b-1", "c-1", "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), "m-pref", null, NOW));
        String reservationId = capacity.reserve("kor-5", ServiceType.CLEANING, NINE_TO_TEN, "j-1",
                NOW.plusMinutes(10)).id();
        job = jobs.save(new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, NINE_TO_TEN, Money.of(249), reservationId,
                "4821", NOW.plusMinutes(10)));
        maids.save(new Maid("m-01", "Lakshmi R", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING), null,
                Tier.STANDARD, "kor-5", 4.6));
        build();
    }

    private void build() {
        service = new PaymentService(new IdempotencyStore(clock, 24),
                new PaymentProcessorRegistry(List.of(new UpiPaymentProcessor(gateway))), payments, jobs, bookings,
                maids, capacity, allocation, clock);
    }

    private void maidAvailable() {
        given(allocation.allocate(any(), eq(AssignmentStatus.ACTIVE))).willReturn(Optional.of(new Assignment("as-1",
                "j-1", "m-01", "s-1", "kor-5", NINE_TO_TEN, NINE_TO_TEN, AssignmentStatus.ACTIVE)));
    }

    private ReservationStatus reservationStatus() {
        return reservations.getById(job.reservationId()).status();
    }

    @Test
    void successfulPaymentCommitsCapacityConfirmsAndAssignsAMaid() {
        maidAvailable();

        IdempotentResult<PaymentReceipt> result = service.pay("j-1", GOOD_UPI, "k-1");

        PaymentReceipt receipt = result.value();
        assertThat(result.replayed()).isFalse();
        assertThat(receipt.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(receipt.jobStatus()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(receipt.maidName()).isEqualTo("Lakshmi R");
        assertThat(receipt.otp()).isEqualTo("4821");
        assertThat(job.paymentId()).isEqualTo(receipt.paymentId());
        assertThat(payments.getById(receipt.paymentId()).gatewayRef()).startsWith("pay_");
        assertThat(reservationStatus()).isEqualTo(ReservationStatus.COMMITTED);

        ArgumentCaptor<AllocationRequest> asked = ArgumentCaptor.forClass(AllocationRequest.class);
        verify(allocation).allocate(asked.capture(), eq(AssignmentStatus.ACTIVE));
        assertThat(asked.getValue().preferredMaidId()).isEqualTo("m-pref");
        assertThat(asked.getValue().work()).isEqualTo(NINE_TO_TEN);
    }

    @Test
    void withNoMaidFreeYetTheJobStaysConfirmed() {
        PaymentReceipt receipt = service.pay("j-1", GOOD_UPI, "k-1").value();

        assertThat(receipt.jobStatus()).isEqualTo(JobStatus.CONFIRMED);
        assertThat(receipt.maidId()).isNull();
        assertThat(reservationStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void aHeldMaidIsActivatedInsteadOfAllocatingAgain() {
        job.holdMaid("m-01", "as-held");

        PaymentReceipt receipt = service.pay("j-1", GOOD_UPI, "k-1").value();

        assertThat(receipt.jobStatus()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(receipt.maidId()).isEqualTo("m-01");
        verify(allocation).activate("j-1");
        verify(allocation, never()).allocate(any(), any());
    }

    @Test
    void aDeclineKeepsTheJobPayableAndANewKeyCanTryAgain() {
        assertThatThrownBy(() -> service.pay("j-1", new UpiDetails("decline@okbank"), "k-1"))
                .isInstanceOfSatisfying(PaymentFailedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_FAILED));

        assertThat(job.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        assertThat(payments.findByJob("j-1")).singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo(PaymentStatus.FAILED));
        assertThat(reservationStatus()).isEqualTo(ReservationStatus.HELD);
        assertThatThrownBy(() -> service.pay("j-1", new UpiDetails("decline@okbank"), "k-1"))
                .as("same key replays the decline").isInstanceOf(PaymentFailedException.class);

        assertThat(service.pay("j-1", GOOD_UPI, "k-2").value().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(job.status()).isEqualTo(JobStatus.CONFIRMED);
    }

    @Test
    void aRepeatedRequestReplaysTheReceiptAndChargesOnce() {
        maidAvailable();
        PaymentReceipt first = service.pay("j-1", GOOD_UPI, "k-1").value();

        IdempotentResult<PaymentReceipt> again = service.pay("j-1", GOOD_UPI, "k-1");

        assertThat(again.replayed()).isTrue();
        assertThat(again.value()).isEqualTo(first);
        assertThat(gateway.moneyMovements()).isEqualTo(1);
        assertThat(payments.findByJob("j-1")).hasSize(1);
    }

    @Test
    void afterTheHoldExpiresNothingIsCharged() {
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.pay("j-1", GOOD_UPI, "k-1"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLD_EXPIRED));
        assertThat(gateway.moneyMovements()).isZero();
        assertThat(payments.findAll()).isEmpty();
    }

    @Test
    void ifTheHoldLapsesWhileChargingTheMoneyIsRefunded() {
        gateway = new MockPaymentGateway() {
            @Override
            public synchronized GatewayResult charge(GatewayChargeRequest request) {
                clock.advance(Duration.ofMinutes(15));  // the customer took too long on the UPI app
                return super.charge(request);
            }
        };
        build();

        assertThatThrownBy(() -> service.pay("j-1", GOOD_UPI, "k-1"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLD_EXPIRED));
        assertThat(payments.findByJob("j-1")).singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED));
        assertThat(gateway.moneyMovements()).as("charge + refund").isEqualTo(2);
        assertThat(job.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        assertThat(reservationStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void aPaidJobCannotBePaidAgain() {
        service.pay("j-1", GOOD_UPI, "k-1");

        assertThatThrownBy(() -> service.pay("j-1", GOOD_UPI, "k-2"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }

    @Test
    void aGatewayOutageFailsThisAttemptAndTheSameKeyCanBeRetried() {
        gateway.failNext();

        assertThatThrownBy(() -> service.pay("j-1", GOOD_UPI, "k-1"))
                .isInstanceOf(GatewayUnavailableException.class);
        assertThat(payments.findByJob("j-1")).singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo(PaymentStatus.FAILED));

        assertThat(service.pay("j-1", GOOD_UPI, "k-1").value().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }
}
