package com.example.maidbooking.allocation.scoring;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;

/** One reason to prefer a maid for a job. Add a rule by adding a bean and (optionally) a weight. */
public interface ScoringRule {

    /** Config key for the weight, e.g. "best-fit-gap". */
    String name();

    /** 0 (worst) to 1 (best). */
    double score(Candidate candidate, AllocationRequest request);
}
