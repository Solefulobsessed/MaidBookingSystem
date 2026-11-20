package com.example.maidbooking.allocation.scoring;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;

/** Favours the maid the customer asked for, or who did their last recurring visit. */
@Component
public class PreferredMaidRule implements ScoringRule {

    @Override
    public String name() {
        return "preferred-maid";
    }

    @Override
    public double score(Candidate candidate, AllocationRequest request) {
        return candidate.maid().id().equals(request.preferredMaidId()) ? 1.0 : 0.0;
    }
}
