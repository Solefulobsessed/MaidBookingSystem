package com.example.maidbooking.booking;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.supply.LiveStateService;
import com.example.maidbooking.supply.LiveStatus;

/**
 * The visit itself: the maid sets off (EN_ROUTE, after which it can't be cancelled), starts with the customer's
 * OTP (IN_PROGRESS), and completes it. Her live state follows, ending IDLE in the job's zone so instant dispatch
 * can send her next.
 */
@Service
public class JobLifecycleService {

    private final JobRepository jobs;
    private final LiveStateService liveStates;
    private final JobGuard guard;

    public JobLifecycleService(JobRepository jobs, LiveStateService liveStates, JobGuard guard) {
        this.jobs = jobs;
        this.liveStates = liveStates;
        this.guard = guard;
    }

    /** The job must be ASSIGNED and the maid checked in and free; nothing changes otherwise. */
    public Job enRoute(String jobId) {
        return guard.exclusive(jobId, () -> {
            Job job = jobs.getById(jobId);
            if (!job.status().canMoveTo(JobStatus.EN_ROUTE)) {
                throw new ConflictException(ErrorCode.INVALID_STATE, "Job " + jobId + " is " + job.status()
                        + "; only an assigned job can set off", Map.of("jobId", jobId));
            }
            liveStates.followJob(job.maidId(), LiveStatus.EN_ROUTE, job.zoneId());
            job.enRoute();
            return jobs.save(job);
        });
    }

    /** A wrong OTP is rejected (400) and changes nothing. */
    public Job start(String jobId, String otp) {
        return guard.exclusive(jobId, () -> {
            Job job = jobs.getById(jobId);
            job.start(otp);
            liveStates.followJob(job.maidId(), LiveStatus.ON_JOB, job.zoneId());
            return jobs.save(job);
        });
    }

    public Job complete(String jobId) {
        return guard.exclusive(jobId, () -> {
            Job job = jobs.getById(jobId);
            job.complete();
            liveStates.followJob(job.maidId(), LiveStatus.IDLE, job.zoneId());
            return jobs.save(job);
        });
    }
}
