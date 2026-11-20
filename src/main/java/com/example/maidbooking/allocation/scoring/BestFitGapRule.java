package com.example.maidbooking.allocation.scoring;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.Candidate;

/**
 * Prefers the maid whose free gap the job fills most tightly, so calendars pack well and larger gaps stay
 * free for later bookings (Urban Company's "best fit"). No idle time left = 1.0; 30 min left = 0.5.
 */
@Component
public class BestFitGapRule implements ScoringRule {

    @Override
    public String name() {
        return "best-fit-gap";
    }

    @Override
    public double score(Candidate candidate, AllocationRequest request) {
        LocalDateTime gapStart = candidate.calendar().previousBefore(candidate.blocked().start())
                .map(a -> a.work().end()).orElse(candidate.shift().window().start());
        LocalDateTime gapEnd = candidate.calendar().nextAfter(request.work().end())
                .map(Assignment::blocked).map(b -> b.start()).orElse(candidate.shift().window().end());
        long leftover = Duration.between(gapStart, candidate.blocked().start()).toMinutes()
                + Duration.between(request.work().end(), gapEnd).toMinutes();
        return 1.0 / (1.0 + Math.max(0, leftover) / 30.0);
    }
}
