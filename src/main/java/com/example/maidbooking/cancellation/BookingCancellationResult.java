package com.example.maidbooking.cancellation;

import java.util.List;

import com.example.maidbooking.common.Money;

/** The visits a booking cancellation cancelled, each with its own refund. */
public record BookingCancellationResult(String bookingId, List<CancellationResult> cancelledJobs) {

    public BookingCancellationResult {
        cancelledJobs = List.copyOf(cancelledJobs);
    }

    public Money totalRefund() {
        return cancelledJobs.stream().map(result -> result.refund().amount()).reduce(Money.ZERO, Money::plus);
    }
}
