package com.example.maidbooking.api.cancellation;

import java.math.BigDecimal;
import java.util.List;

import com.example.maidbooking.cancellation.BookingCancellationResult;

public record BookingCancellationResponse(String bookingId, List<CancellationResponse> cancelledJobs,
                                          BigDecimal totalRefund) {

    static BookingCancellationResponse from(BookingCancellationResult result) {
        return new BookingCancellationResponse(result.bookingId(),
                result.cancelledJobs().stream().map(CancellationResponse::from).toList(),
                result.totalRefund().amount());
    }
}
