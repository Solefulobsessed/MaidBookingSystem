package com.example.maidbooking.allocation.scoring;

import java.util.Map;

import com.example.maidbooking.allocation.Candidate;

/** A candidate's total score and each rule's weighted part, so a choice can be explained. */
public record ScoredCandidate(Candidate candidate, double total, Map<String, Double> reasons) {
}
