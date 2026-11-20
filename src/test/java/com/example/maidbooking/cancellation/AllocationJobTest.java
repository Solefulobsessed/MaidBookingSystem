package com.example.maidbooking.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.booking.SubscriptionRepository;
import com.example.maidbooking.capacity.BucketKey;
import com.example.maidbooking.capacity.CapacityBucketRepository;
import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.capacity.CapacityReservationRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.capacity.ReservationStatus;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.payment.GatewayChargeRequest;
import com.example.maidbooking.payment.MockPaymentGateway;
import com.example.maidbooking.payment.Payment;
import com.example.maidbooking.payment.PaymentMethodType;
import com.example.maidbooking.payment.PaymentProcessorRegistry;
import com.example.maidbooking.payment.PaymentRepository;
import com.example.maidbooking.payment.PaymentStatus;
import com.example.maidbooking.payment.UpiPaymentProcessor;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class AllocationJobTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private final MutableClock clock = new MutableClock(MON.atTime(6, 0));
    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityReservationRepository reservations = new CapacityReservationRepository();
    private final CapacityService capacity = new CapacityService(buckets, reservations, new InMemoryLockRegistry(),
            event -> { }, clock, new CapacityProperties(1.0));
    private final JobRepository jobs = new JobRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final PaymentRepository payments = new PaymentRepository();
    private final MockPaymentGateway gateway = new MockPaymentGateway();
    private final AllocationService allocation = mock(AllocationService.class);
    private final JobGuard guard = new JobGuard();
    private AllocationJob allocationJob;

    @BeforeEach
    void setUp() {
        CancellationService cancellations = new CancellationService(
                new TimeBasedCancellationPolicy(new CancellationProperties(null, null, null)), jobs, bookings,
                new SubscriptionRepository(), payments,
                new PaymentProcessorRegistry(List.of(new UpiPaymentProcessor(gateway))), allocation, capacity, guard,
                clock);
        allocationJob = new AllocationJob(jobs, bookings, allocation, cancellations, guard, clock, 60);
        bookings.save(new Booking("b-1", "c-1", "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), "m-pref", null, MON.minusDays(1).atTime(9, 0)));
        for (LocalDateTime t = MON.atTime(6, 0); t.isBefore(MON.atTime(13, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, t)).addCapacity(60);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(60);
        }
    }

    /** A paid job (CONFIRMED) at {@code hour:minute} for an hour, still without a maid. */
    private Job paidJob(String id, int hour, int minute) {
        TimeSlot slot = TimeSlot.of(MON.atTime(hour, minute), Duration.ofHours(1));
        String reservationId = capacity.reserve("kor-5", ServiceType.CLEANING, slot, id, null).id();
        capacity.commit(reservationId);
        Payment payment = payments.save(new Payment("p-" + id, id, Money.of(249), PaymentMethodType.UPI, "k-" + id));
        payment.succeed(gateway.charge(new GatewayChargeRequest("k-" + id, Money.of(249), "UPI:user@okbank"))
                .gatewayRef());
        Job job = jobs.save(new Job(id, "b-1", "kor-5", ServiceType.CLEANING, slot, Money.of(249), reservationId,
                "4821", null));
        job.confirm(payment.id());
        return job;
    }

    private void maidFor(String jobId) {
        given(allocation.allocate(argThat(request -> request != null && request.referenceId().equals(jobId)),
                eq(AssignmentStatus.ACTIVE))).willAnswer(call -> Optional.of(new Assignment("as-" + jobId, jobId,
                "m-01", "s-1", "kor-5", jobs.getById(jobId).slot(), jobs.getById(jobId).slot(),
                AssignmentStatus.ACTIVE)));
    }

    @Test
    void aJobWithoutAMaidGetsOneOnALaterRun() {
        Job job = paidJob("j-9", 9, 0);

        assertThat(allocationJob.retry()).isEqualTo(new AllocationRetryResult(0, 0, 1));
        assertThat(job.status()).isEqualTo(JobStatus.CONFIRMED);

        maidFor("j-9");
        clock.set(MON.atTime(6, 5));
        assertThat(allocationJob.retry()).isEqualTo(new AllocationRetryResult(1, 0, 0));
        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(job.maidId()).isEqualTo("m-01");
        verify(allocation, atLeastOnce())
                .allocate(argThat(request -> "m-pref".equals(request.preferredMaidId())), eq(AssignmentStatus.ACTIVE));
    }

    @Test
    void atTheCutoffTheJobIsUnfulfilledAndFullyRefunded() {
        Job job = paidJob("j-0645", 6, 45);  // 45 minutes away, inside the 60-minute cutoff

        assertThat(allocationJob.retry()).isEqualTo(new AllocationRetryResult(0, 1, 0));

        assertThat(job.status()).isEqualTo(JobStatus.UNFULFILLED);
        Payment payment = payments.getById("p-j-0645");
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundedAmount()).isEqualTo(Money.of(249));
        assertThat(reservations.getById(job.reservationId()).status()).isEqualTo(ReservationStatus.RELEASED);
        verify(allocation, never()).allocate(any(), any());
    }

    @Test
    void exactlyAtTheCutoffCountsAsTooLate() {
        Job job = paidJob("j-7", 7, 0);

        allocationJob.retry();

        assertThat(job.status()).isEqualTo(JobStatus.UNFULFILLED);
    }

    @Test
    void earliestJobsAreTriedFirstAndAssignedJobsAreLeftAlone() {
        paidJob("j-11", 11, 0);
        paidJob("j-9", 9, 0);
        Job assigned = paidJob("j-10", 10, 0);
        assigned.assign("m-02", "as-x");

        allocationJob.retry();

        InOrder order = inOrder(allocation);
        order.verify(allocation).allocate(argThat(r -> r.referenceId().equals("j-9")), any());
        order.verify(allocation).allocate(argThat(r -> r.referenceId().equals("j-11")), any());
        verify(allocation, never()).allocate(argThat(r -> r.referenceId().equals("j-10")), any());
    }

    @Test
    void aJobBeingCancelledRightNowIsSkippedThisRun() {
        paidJob("j-9", 9, 0);
        maidFor("j-9");

        AllocationRetryResult result = guard.exclusive("j-9", allocationJob::retry);

        assertThat(result).isEqualTo(new AllocationRetryResult(0, 0, 1));
        assertThat(jobs.getById("j-9").status()).isEqualTo(JobStatus.CONFIRMED);
    }
}
