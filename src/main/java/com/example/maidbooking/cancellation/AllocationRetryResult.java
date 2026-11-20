package com.example.maidbooking.cancellation;

/** One allocation retry run: jobs that got a maid, jobs given up at the cutoff, and jobs still waiting. */
public record AllocationRetryResult(int assigned, int unfulfilled, int stillWaiting) {
}
