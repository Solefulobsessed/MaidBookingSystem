package com.example.maidbooking.cancellation;

import java.time.LocalDateTime;
import java.util.Map;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.payment.Payment;

/**
 * Decides the refund when a job is cancelled. One policy is active, picked by {@code app.cancellation.policy}.
 * Every policy refuses jobs that can no longer be cancelled (maid already on her way).
 */
public interface CancellationPolicy {

    /** @param payment the job's successful payment, or {@code null} if it was never paid */
    RefundDecision evaluate(Job job, Payment payment, CancelledBy who, LocalDateTime at);

    /** @throws ConflictException INVALID_STATE once the maid is en route or the job is over */
    static void requireCancellable(Job job) {
        if (!job.status().isCancellable()) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Job " + job.id() + " is " + job.status()
                    + " and can no longer be cancelled", Map.of("jobId", job.id()));
        }
    }

    /** {@code percent} of what was paid, never more than is still refundable. */
    static RefundDecision refund(Payment payment, int percent, String reason) {
        if (payment == null) {
            return new RefundDecision(0, Money.ZERO, "Nothing was paid");
        }
        Money amount = payment.amount().percent(percent);
        return new RefundDecision(percent, amount.compareTo(payment.refundable()) > 0 ? payment.refundable() : amount,
                reason);
    }
}
