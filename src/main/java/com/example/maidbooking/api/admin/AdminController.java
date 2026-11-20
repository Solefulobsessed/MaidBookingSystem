package com.example.maidbooking.api.admin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.common.ManualJob;
import com.example.maidbooking.common.error.NotFoundException;

/**
 * Runs a background job now, for demos and tests: {@code allocation}, {@code subscriptions},
 * {@code recurring-charge} or {@code hold-sweeper}. Built from every {@link ManualJob} bean.
 */
@RestController
public class AdminController {

    public record JobRun(String job, Object result) {
    }

    private final Map<String, ManualJob> jobs;

    public AdminController(List<ManualJob> jobs) {
        this.jobs = Map.copyOf(jobs.stream().collect(Collectors.toMap(ManualJob::name, Function.identity())));
    }

    @PostMapping("/api/v1/admin/run/{job}")
    public JobRun run(@PathVariable String job) {
        ManualJob manual = jobs.get(job);
        if (manual == null) {
            throw new NotFoundException("Job", job + " (known: " + Set.copyOf(jobs.keySet()) + ")");
        }
        return new JobRun(job, manual.runNow());
    }
}
