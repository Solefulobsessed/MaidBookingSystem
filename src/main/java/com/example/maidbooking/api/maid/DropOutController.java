package com.example.maidbooking.api.maid;

import java.util.Set;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.cancellation.DropOutService;

@RestController
public class DropOutController {

    /** {@code maidId} is the replacement, or null while the job waits for one (status CONFIRMED). */
    public record DropOutResponse(String jobId, JobStatus status, String maidId, Set<String> droppedBy) {
    }

    private final DropOutService dropOuts;

    public DropOutController(DropOutService dropOuts) {
        this.dropOuts = dropOuts;
    }

    @PostMapping("/api/v1/maids/{maidId}/jobs/{jobId}/drop")
    public DropOutResponse drop(@PathVariable String maidId, @PathVariable String jobId) {
        Job job = dropOuts.dropJob(maidId, jobId);
        return new DropOutResponse(job.id(), job.status(), job.maidId(), job.droppedBy());
    }
}
