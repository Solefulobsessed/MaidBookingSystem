package com.example.maidbooking.cancellation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refund windows for the time-based policy: more than {@code fullRefundHours} before the start refunds
 * everything, at least {@code partialRefundHours} before refunds {@code partialRefundPercent}, later nothing.
 */
@ConfigurationProperties(prefix = "app.cancellation")
public record CancellationProperties(Integer fullRefundHours, Integer partialRefundHours,
                                     Integer partialRefundPercent) {

    public CancellationProperties {
        fullRefundHours = fullRefundHours == null ? 4 : fullRefundHours;
        partialRefundHours = partialRefundHours == null ? 1 : partialRefundHours;
        partialRefundPercent = partialRefundPercent == null ? 50 : partialRefundPercent;
        if (partialRefundHours < 0 || fullRefundHours < partialRefundHours
                || partialRefundPercent < 0 || partialRefundPercent > 100) {
            throw new IllegalArgumentException("app.cancellation needs 0 <= partial-refund-hours <= "
                    + "full-refund-hours and a percent between 0 and 100");
        }
    }
}
