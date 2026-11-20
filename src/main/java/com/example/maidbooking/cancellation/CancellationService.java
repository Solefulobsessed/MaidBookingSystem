package com.example.maidbooking.cancellation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.Subscription;
import com.example.maidbooking.booking.SubscriptionRepository;
import com.example.maidbooking.booking.SubscriptionStatus;
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
    private final BookingRepository bookings;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final PaymentProcessorRegistry processors;
    private final AllocationService allocation;
    private final CapacityService capacity;
    private final JobGuard guard;
    private final Clock clock;

    public CancellationService(CancellationPolicy policy, JobRepository jobs, BookingRepository bookings,
                               SubscriptionRepository subscriptions, PaymentRepository payments,
                               PaymentProcessorRegistry processors, AllocationService allocation,
                               CapacityService capacity, JobGuard guard, Clock clock) {
        this.policy = policy;
        this.jobs = jobs;
        this.bookings = bookings;
        this.subscriptions = subscriptions;
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

    /**
     * Cancels everything still to come: the subscription first (so no new visits are generated), then every
     * future visit that can still be cancelled, each refunded by the policy. Past and in-progress visits are left
     * alone. If one refund fails the call stops there; calling again finishes the rest.
     */
    public BookingCancellationResult cancelBooking(String bookingId) {
        Booking booking = bookings.getById(bookingId);
        if (booking.subscriptionId() != null) {
            subscriptions.findById(booking.subscriptionId())
                    .filter(subscription -> subscription.status() != SubscriptionStatus.CANCELLED)
                    .ifPresent(Subscription::cancel);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<CancellationResult> cancelled = jobs.findByBooking(bookingId).stream()
                .filter(job -> job.status().isCancellable() && job.slot().start().isAfter(now))
                .map(job -> cancelJob(job.id(), CancelledBy.CUSTOMER))
                .toList();
        return new BookingCancellationResult(bookingId, cancelled);
    }

    private void refund(Payment payment, RefundDecision decision) {
        GatewayResult result = processors.processorFor(payment.method()).refund(payment, decision.amount());
        if (!result.approved()) {
            payment.noteRefundDeclined();
            throw new GatewayUnavailableException("Refund failed (" + result.declineReason()
                    + "); the job was not cancelled, please try again");
        }
        payment.recordRefund(decision.amount());
    }

    private Payment paymentOf(Job job) {
        return job.paymentId() == null ? null : payments.getById(job.paymentId());
    }
}
