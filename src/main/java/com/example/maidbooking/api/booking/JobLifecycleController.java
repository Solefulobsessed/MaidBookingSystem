package com.example.maidbooking.api.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobLifecycleService;
import com.example.maidbooking.booking.JobStatus;

/** Called from the maid's app as the visit happens. */
@RestController
@RequestMapping("/api/v1/jobs/{jobId}")
public class JobLifecycleController {

    public record StartBody(@NotBlank String otp) {
    }

    public record JobProgress(String jobId, JobStatus status, String maidId) {

        static JobProgress of(Job job) {
            return new JobProgress(job.id(), job.status(), job.maidId());
        }
    }

    private final JobLifecycleService lifecycle;

    public JobLifecycleController(JobLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/en-route")
    public JobProgress enRoute(@PathVariable String jobId) {
        return JobProgress.of(lifecycle.enRoute(jobId));
    }

    @PostMapping("/start")
    public JobProgress start(@PathVariable String jobId, @Valid @RequestBody StartBody body) {
        return JobProgress.of(lifecycle.start(jobId, body.otp()));
    }

    @PostMapping("/complete")
    public JobProgress complete(@PathVariable String jobId) {
        return JobProgress.of(lifecycle.complete(jobId));
    }
}
