package com.example.maidbooking.cancellation;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.payment.Payment;

/** The default: 100% more than 4 h before, 50% from 4 h to 1 h before, 0% after; platform-caused is 100%. */
@Component
@ConditionalOnProperty(name = "app.cancellation.policy", havingValue = "time-based", matchIfMissing = true)
public class TimeBasedCancellationPolicy implements CancellationPolicy {

    private final CancellationProperties properties;

    public TimeBasedCancellationPolicy(CancellationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RefundDecision evaluate(Job job, Payment payment, CancelledBy who, LocalDateTime at) {
        CancellationPolicy.requireCancellable(job);
        if (who == CancelledBy.PLATFORM) {
            return CancellationPolicy.refund(payment, 100, "We couldn't serve this visit");
        }
        Duration beforeStart = Duration.between(at, job.slot().start());
        if (beforeStart.compareTo(Duration.ofHours(properties.fullRefundHours())) > 0) {
            return CancellationPolicy.refund(payment, 100, "Cancelled more than " + properties.fullRefundHours()
                    + " h before the start");
        }
        if (beforeStart.compareTo(Duration.ofHours(properties.partialRefundHours())) >= 0) {
            return CancellationPolicy.refund(payment, properties.partialRefundPercent(), "Cancelled within "
                    + properties.fullRefundHours() + " h of the start");
        }
        return CancellationPolicy.refund(payment, 0, "Cancelled less than " + properties.partialRefundHours()
                + " h before the start");
    }
}
