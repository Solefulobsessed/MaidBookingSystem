package com.example.maidbooking.payment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;

/**
 * Pays for one job: idempotency → job still payable and held → charge → commit capacity → confirm → activate
 * the held maid or allocate one. If no maid is free yet the job stays CONFIRMED and is retried later.
 */
@Service
public class PaymentService {

    private final IdempotencyStore idempotency;
    private final PaymentProcessorRegistry processors;
    private final PaymentRepository payments;
    private final JobRepository jobs;
    private final BookingRepository bookings;
    private final MaidRepository maids;
    private final CapacityService capacity;
    private final AllocationService allocation;
    private final Clock clock;
    private final JobGuard guard;

    public PaymentService(IdempotencyStore idempotency, PaymentProcessorRegistry processors, PaymentRepository payments,
                          JobRepository jobs, BookingRepository bookings, MaidRepository maids,
                          CapacityService capacity, AllocationService allocation, JobGuard guard, Clock clock) {
        this.idempotency = idempotency;
        this.processors = processors;
        this.payments = payments;
        this.jobs = jobs;
        this.bookings = bookings;
        this.maids = maids;
        this.capacity = capacity;
        this.allocation = allocation;
        this.guard = guard;
        this.clock = clock;
    }

    public IdempotentResult<PaymentReceipt> pay(String jobId, PaymentDetails details, String idempotencyKey) {
        String fingerprint = IdempotencyStore.fingerprint(jobId + "|" + details);
        // the guard is outside the store, so "busy, try again" is never recorded as this key's answer
        return guard.exclusive(jobId,
                () -> idempotency.execute(idempotencyKey, fingerprint, () -> payOnce(jobId, details, idempotencyKey)));
    }

    private PaymentReceipt payOnce(String jobId, PaymentDetails details, String idempotencyKey) {
        Job job = payableJob(jobId);
        PaymentProcessor processor = processors.processorFor(details.method());
        processor.validate(details);
        Payment payment = payments.save(new Payment("p-" + UUID.randomUUID().toString().substring(0, 8), jobId,
                job.price(), details.method(), idempotencyKey));
        GatewayResult result = charge(processor, payment, details);
        if (!result.approved()) {
            payment.fail();
            throw new PaymentFailedException(payment.id(), result.declineReason());
        }
        payment.succeed(result.gatewayRef());
        commitOrRefund(job, payment, processor);
        job.confirm(payment.id());
        findMaid(job);
        return receipt(payment, jobs.save(job));
    }

    private Job payableJob(String jobId) {
        Job job = jobs.getById(jobId);
        if (job.status() != JobStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Job " + jobId + " is " + job.status()
                    + "; nothing to pay", Map.of("jobId", jobId));
        }
        if (job.holdExpiresAt() != null && !LocalDateTime.now(clock).isBefore(job.holdExpiresAt())) {
            throw new ConflictException(ErrorCode.HOLD_EXPIRED, "The hold on this slot expired at "
                    + job.holdExpiresAt() + "; please book again", Map.of("jobId", jobId));
        }
        return job;
    }

    private static GatewayResult charge(PaymentProcessor processor, Payment payment, PaymentDetails details) {
        try {
            return processor.charge(payment.amount(), details, payment.idempotencyKey());
        } catch (GatewayUnavailableException outage) {
            payment.fail();
            throw outage;
        }
    }

    /** The hold can lapse while the gateway is charging; then the money goes straight back. */
    private void commitOrRefund(Job job, Payment payment, PaymentProcessor processor) {
        try {
            capacity.commit(job.reservationId());
        } catch (RuntimeException lapsed) {
            if (processor.refund(payment, payment.amount()).approved()) {
                payment.recordRefund(payment.amount());
            }
            throw lapsed;
        }
    }

    private void findMaid(Job job) {
        if (job.status() == JobStatus.ASSIGNED) {
            allocation.activate(job.id());
            return;
        }
        Booking booking = bookings.getById(job.bookingId());
        allocation.allocate(new AllocationRequest(job.id(), job.zoneId(), job.service(), job.slot(),
                        booking.constraints(), booking.preferredMaidId(), job.droppedBy()),
                        AssignmentStatus.ACTIVE)
                .ifPresent(assigned -> job.assign(assigned.maidId(), assigned.id()));
    }

    private PaymentReceipt receipt(Payment payment, Job job) {
        Maid maid = job.maidId() == null ? null : maids.getById(job.maidId());
        return new PaymentReceipt(payment.id(), payment.status(), job.id(), job.status(),
                maid == null ? null : maid.id(), maid == null ? null : maid.name(),
                maid == null ? null : maid.rating(), job.otp());
    }
}
