package com.example.maidbooking.allocation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.scoring.MaidScorer;
import com.example.maidbooking.allocation.scoring.ScoredCandidate;

/** Orders candidates by {@link MaidScorer}: best fit, preferred maid, travel, fairness and rating. */
@Component
public class BestFitAllocationStrategy implements AllocationStrategy {

    private final MaidScorer scorer;

    public BestFitAllocationStrategy(MaidScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public List<Candidate> order(List<Candidate> candidates, AllocationRequest request) {
        return scorer.rank(candidates, request).stream().map(ScoredCandidate::candidate).toList();
    }
}
