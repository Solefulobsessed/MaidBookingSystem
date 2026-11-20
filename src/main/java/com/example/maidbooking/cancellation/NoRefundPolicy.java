package com.example.maidbooking.cancellation;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.payment.Payment;

/** For non-refundable offers: customer cancellations get nothing back; platform-caused ones still get 100%. */
@Component
@ConditionalOnProperty(name = "app.cancellation.policy", havingValue = "no-refund")
public class NoRefundPolicy implements CancellationPolicy {

    @Override
    public RefundDecision evaluate(Job job, Payment payment, CancelledBy who, LocalDateTime at) {
        CancellationPolicy.requireCancellable(job);
        return who == CancelledBy.PLATFORM
                ? CancellationPolicy.refund(payment, 100, "We couldn't serve this visit")
                : CancellationPolicy.refund(payment, 0, "This booking is non-refundable");
    }
}
