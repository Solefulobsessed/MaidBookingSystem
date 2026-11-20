package com.example.maidbooking.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.error.DomainException;

/**
 * Charges each later visit of a series with the saved method once it is within 24 hours, using the key
 * {@code jobId:charge} so a re-run never charges twice. If a visit can't be charged (declined, or no saved
 * method) only that visit is cancelled, with its capacity and maid released. A gateway outage is retried on the
 * next run.
 */
@Component
public class RecurringChargeJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringChargeJob.class);

    private final JobRepository jobs;
    private final BookingRepository bookings;
    private final SavedPaymentMethods savedMethods;
    private final PaymentService payments;
    private final CapacityService capacity;
    private final AllocationService allocation;
    private final Clock clock;
    private final Duration chargeAhead;

    public RecurringChargeJob(JobRepository jobs, BookingRepository bookings, SavedPaymentMethods savedMethods,
                              PaymentService payments, CapacityService capacity, AllocationService allocation,
                              Clock clock, @Value("${app.recurring.charge-hours-before:24}") long chargeHoursBefore) {
        this.jobs = jobs;
        this.bookings = bookings;
        this.savedMethods = savedMethods;
        this.payments = payments;
        this.capacity = capacity;
        this.allocation = allocation;
        this.clock = clock;
        this.chargeAhead = Duration.ofHours(chargeHoursBefore);
    }

    @Scheduled(fixedDelayString = "${app.recurring.charge-interval-ms:900000}")
    public void run() {
        chargeDue();
    }

    public RecurringChargeResult chargeDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int charged = 0;
        int cancelled = 0;
        for (Job job : dueVisits(now)) {
            Booking booking = bookings.getById(job.bookingId());
            Optional<PaymentDetails> method = savedMethods.find(booking.subscriptionId());
            if (method.isEmpty()) {
                cancel(job, "no saved payment method");
                cancelled++;
                continue;
            }
            try {
                payments.pay(job.id(), method.get(), job.id() + ":charge");
                charged++;
            } catch (GatewayUnavailableException outage) {
                log.warn("Charging visit {} failed (gateway down); retrying next run", job.id());
            } catch (DomainException failed) {
                cancel(job, failed.getMessage());
                cancelled++;
            }
        }
        return new RecurringChargeResult(charged, cancelled);
    }

    /** Unpaid visits of a series, without a payment hold, starting within the next 24 hours. */
    private List<Job> dueVisits(LocalDateTime now) {
        return jobs.findByStatus(JobStatus.PENDING_PAYMENT).stream()
                .filter(job -> job.holdExpiresAt() == null)
                .filter(job -> job.slot().start().isAfter(now) && !job.slot().start().isAfter(now.plus(chargeAhead)))
                .filter(job -> bookings.getById(job.bookingId()).subscriptionId() != null)
                .toList();
    }

    private void cancel(Job job, String reason) {
        job.cancel();
        allocation.release(job.id());
        capacity.release(job.reservationId());
        jobs.save(job);
        log.info("Visit {} cancelled: could not be charged ({}); the rest of the series is unaffected", job.id(), reason);
    }
}
