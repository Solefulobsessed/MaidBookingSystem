package com.example.maidbooking.allocation;

import java.util.List;

/** Orders feasible candidates, best first. Swap the bean to change how maids are chosen. */
public interface AllocationStrategy {

    List<Candidate> order(List<Candidate> candidates, AllocationRequest request);
}
