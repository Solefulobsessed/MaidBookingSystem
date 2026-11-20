package com.example.maidbooking.cancellation;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.AssignmentRepository;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobGuard;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.supply.ShiftChanged;

/**
 * A maid can't do a job she was given: she drops it, or her shift stops counting (leave, absence, ending early).
 * Her time is freed and the job goes straight back to allocation without her. The customer keeps the slot and
 * the payment, and the capacity reservation is untouched. If nobody else is free, the allocation retry job
 * keeps trying until the cutoff.
 */
@Service
public class DropOutService {

    private static final Logger log = LoggerFactory.getLogger(DropOutService.class);

    private final JobRepository jobs;
    private final BookingRepository bookings;
    private final AssignmentRepository assignments;
    private final AllocationService allocation;
    private final JobGuard guard;

    public DropOutService(JobRepository jobs, BookingRepository bookings, AssignmentRepository assignments,
                          AllocationService allocation, JobGuard guard) {
        this.jobs = jobs;
        this.bookings = bookings;
        this.assignments = assignments;
        this.allocation = allocation;
        this.guard = guard;
    }

    public Job dropJob(String maidId, String jobId) {
        return guard.exclusive(jobId, () -> {
            Job job = jobs.getById(jobId);
            if (!maidId.equals(job.maidId())) {
                throw new ConflictException(ErrorCode.INVALID_STATE, "Job " + jobId + " is not assigned to maid "
                        + maidId, Map.of("jobId", jobId));
            }
            return reallocateWithout(job);
        });
    }

    /** Leave or absence removed a shift: every job on it is handed to someone else. */
    @EventListener
    public void onShiftChanged(ShiftChanged event) {
        if (event.change() != ShiftChanged.Change.CAPACITY_REMOVED) {
            return;
        }
        assignments.findLiveByMaid(event.maidId()).stream()
                .filter(assignment -> assignment.shiftId().equals(event.shiftId()))
                .forEach(assignment -> jobs.findById(assignment.referenceId()).ifPresent(job -> {
                    try {
                        guard.exclusive(job.id(), () -> reallocateWithout(job));
                    } catch (DomainException e) {
                        log.warn("Could not hand job {} to another maid: {}", job.id(), e.getMessage());
                    }
                }));
    }

    private Job reallocateWithout(Job job) {
        allocation.release(job.id());
        if (job.status() == JobStatus.PENDING_PAYMENT) {
            job.releaseHeldMaid();  // payment will look for a maid again
            return jobs.save(job);
        }
        job.unassign();
        Booking booking = bookings.getById(job.bookingId());
        allocation.allocate(new AllocationRequest(job.id(), job.zoneId(), job.service(), job.slot(),
                        booking.constraints(), booking.preferredMaidId(), job.droppedBy()), AssignmentStatus.ACTIVE)
                .ifPresent(assignment -> job.assign(assignment.maidId(), assignment.id()));
        return jobs.save(job);
    }
}
