package com.example.maidbooking.cancellation;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.payment.GatewayResult;
import com.example.maidbooking.payment.GatewayUnavailableException;
import com.example.maidbooking.payment.Payment;
import com.example.maidbooking.payment.PaymentProcessorRegistry;
import com.example.maidbooking.payment.PaymentRepository;

/**
 * Cancels one job. The refund happens first: if it fails, nothing else changes. Then the maid and the capacity are
 * released, so the slot is bookable again at once, and finally the job moves to CANCELLED.
 */
@Service
public class CancellationService {

    private final CancellationPolicy policy;
    private final JobRepository jobs;
    private final PaymentRepository payments;
    private final PaymentProcessorRegistry processors;
    private final AllocationService allocation;
    private final CapacityService capacity;
    private final JobGuard guard;
    private final Clock clock;

    public CancellationService(CancellationPolicy policy, JobRepository jobs, PaymentRepository payments,
                               PaymentProcessorRegistry processors, AllocationService allocation,
                               CapacityService capacity, JobGuard guard, Clock clock) {
        this.policy = policy;
        this.jobs = jobs;
        this.payments = payments;
        this.processors = processors;
        this.allocation = allocation;
        this.capacity = capacity;
        this.guard = guard;
        this.clock = clock;
    }

    /** What the customer would get back if they cancelled now. Changes nothing. */
    public RefundDecision quote(String jobId) {
        Job job = jobs.getById(jobId);
        return policy.evaluate(job, paymentOf(job), CancelledBy.CUSTOMER, LocalDateTime.now(clock));
    }

    public CancellationResult cancelJob(String jobId, CancelledBy who) {
        return guard.exclusive(jobId, () -> {
            Job job = jobs.getById(jobId);
            Payment payment = paymentOf(job);
            RefundDecision decision = policy.evaluate(job, payment, who, LocalDateTime.now(clock));
            if (!decision.amount().isZero()) {
                refund(payment, decision);
            }
            allocation.release(job.id());
            capacity.releaseIfLive(job.reservationId());
            job.cancel();
            jobs.save(job);
            return new CancellationResult(job.id(), job.status(), decision);
        });
    }

    private void refund(Payment payment, RefundDecision decision) {
        GatewayResult result = processors.processorFor(payment.method()).refund(payment, decision.amount());
        if (!result.approved()) {
            throw new GatewayUnavailableException("Refund failed (" + result.declineReason()
                    + "); the job was not cancelled, please try again");
        }
        payment.recordRefund(decision.amount());
    }

    private Payment paymentOf(Job job) {
        return job.paymentId() == null ? null : payments.getById(job.paymentId());
    }
}
