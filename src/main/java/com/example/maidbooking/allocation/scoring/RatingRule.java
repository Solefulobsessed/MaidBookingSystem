package com.example.maidbooking.allocation.scoring;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;

/** Prefers better-rated maids: rating / 5. Unrated maids get a neutral 0.5 so new maids still get work. */
@Component
public class RatingRule implements ScoringRule {

    static final double UNRATED = 0.5;

    @Override
    public String name() {
        return "rating";
    }

    @Override
    public double score(Candidate candidate, AllocationRequest request) {
        return candidate.maid().isRated() ? candidate.maid().rating() / 5.0 : UNRATED;
    }
}
