package com.example.maidbooking.allocation.scoring;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;

/**
 * Spreads work: a maid with fewer jobs that day scores higher, so nobody sits idle while others are
 * overloaded (the spec's "starvation score"). 0 jobs = 1.0, 1 = 0.5, 3 = 0.25.
 */
@Component
public class FairnessRule implements ScoringRule {

    @Override
    public String name() {
        return "fairness";
    }

    @Override
    public double score(Candidate candidate, AllocationRequest request) {
        long jobsToday = candidate.calendar().jobsOn(request.work().start().toLocalDate());
        return 1.0 / (1.0 + jobsToday);
    }
}
