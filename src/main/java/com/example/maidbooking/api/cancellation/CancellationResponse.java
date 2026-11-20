package com.example.maidbooking.api.cancellation;

import java.math.BigDecimal;

import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.cancellation.CancellationResult;
import com.example.maidbooking.cancellation.RefundDecision;

/** A cancellation (or, with {@code jobStatus} null, a quote for one). */
public record CancellationResponse(String jobId, JobStatus jobStatus, int refundPercent, BigDecimal refundAmount,
                                   String reason) {

    static CancellationResponse quote(String jobId, RefundDecision decision) {
        return new CancellationResponse(jobId, null, decision.percent(), decision.amount().amount(), decision.reason());
    }

    static CancellationResponse from(CancellationResult result) {
        RefundDecision refund = result.refund();
        return new CancellationResponse(result.jobId(), result.jobStatus(), refund.percent(), refund.amount().amount(),
                refund.reason());
    }
}
