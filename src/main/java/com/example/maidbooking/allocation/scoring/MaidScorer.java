package com.example.maidbooking.allocation.scoring;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Candidate;

/** Ranks candidates by the weighted sum of every {@link ScoringRule}; ties go to the lower maid id. */
@Component
public class MaidScorer {

    private final List<ScoringRule> rules;
    private final ScoringProperties properties;

    public MaidScorer(List<ScoringRule> rules, ScoringProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    public List<ScoredCandidate> rank(List<Candidate> candidates, AllocationRequest request) {
        return candidates.stream().map(candidate -> score(candidate, request))
                .sorted(Comparator.comparingDouble(ScoredCandidate::total).reversed()
                        .thenComparing(scored -> scored.candidate().maid().id()))
                .toList();
    }

    ScoredCandidate score(Candidate candidate, AllocationRequest request) {
        Map<String, Double> reasons = new LinkedHashMap<>();
        for (ScoringRule rule : rules) {
            reasons.put(rule.name(), properties.weightOf(rule.name()) * rule.score(candidate, request));
        }
        return new ScoredCandidate(candidate, reasons.values().stream().mapToDouble(Double::doubleValue).sum(), reasons);
    }
}
