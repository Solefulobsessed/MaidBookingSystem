package com.example.maidbooking.cancellation;

import com.example.maidbooking.booking.JobStatus;

public record CancellationResult(String jobId, JobStatus jobStatus, RefundDecision refund) {
}
