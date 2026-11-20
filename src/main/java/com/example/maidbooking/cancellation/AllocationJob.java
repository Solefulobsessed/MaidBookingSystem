package com.example.maidbooking.cancellation;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.common.error.DomainException;

/**
 * Every 5 minutes, tries again to find a maid for paid jobs that have none (CONFIRMED), earliest start first.
 * A job still without a maid at the cutoff (60 min before the start) becomes UNFULFILLED with a full refund.
 */
@Component
public class AllocationJob {

    private static final Logger log = LoggerFactory.getLogger(AllocationJob.class);

    private final JobRepository jobs;
    private final BookingRepository bookings;
    private final AllocationService allocation;
    private final CancellationService cancellations;
    private final JobGuard guard;
    private final Clock clock;
    private final Duration cutoff;

    public AllocationJob(JobRepository jobs, BookingRepository bookings, AllocationService allocation,
                         CancellationService cancellations, JobGuard guard, Clock clock,
                         @Value("${app.allocation.cutoff-minutes:60}") long cutoffMinutes) {
        this.jobs = jobs;
        this.bookings = bookings;
        this.allocation = allocation;
        this.cancellations = cancellations;
        this.guard = guard;
        this.clock = clock;
        this.cutoff = Duration.ofMinutes(cutoffMinutes);
    }

    @Scheduled(fixedDelayString = "${app.allocation.retry-interval-ms:300000}")
    public void run() {
        retry();
    }

    public AllocationRetryResult retry() {
        LocalDateTime now = LocalDateTime.now(clock);
        int assigned = 0;
        int unfulfilled = 0;
        int waiting = 0;
        for (Job job : jobs.findByStatus(JobStatus.CONFIRMED)) {
            try {
                if (!job.slot().start().isAfter(now.plus(cutoff))) {
                    cancellations.markUnfulfilled(job.id());
                    log.warn("Job {} in zone {} at {} UNFULFILLED: no maid by the cutoff (supply gap)",
                            job.id(), job.zoneId(), job.slot().start());
                    unfulfilled++;
                } else if (guard.exclusive(job.id(), () -> assign(job))) {
                    assigned++;
                } else {
                    waiting++;
                }
            } catch (DomainException e) {
                log.warn("Retrying job {} failed: {}", job.id(), e.getMessage());
                waiting++;
            }
        }
        return new AllocationRetryResult(assigned, unfulfilled, waiting);
    }

    private boolean assign(Job job) {
        if (job.status() != JobStatus.CONFIRMED) {
            return false;  // cancelled or assigned since the list was read
        }
        Booking booking = bookings.getById(job.bookingId());
        Optional<Assignment> found = allocation.allocate(new AllocationRequest(job.id(), job.zoneId(), job.service(),
                job.slot(), booking.constraints(), booking.preferredMaidId(), job.droppedBy()),
                AssignmentStatus.ACTIVE);
        found.ifPresent(assignment -> {
            job.assign(assignment.maidId(), assignment.id());
            jobs.save(job);
        });
        return found.isPresent();
    }
}
