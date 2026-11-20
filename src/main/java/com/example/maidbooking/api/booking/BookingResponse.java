package com.example.maidbooking.api.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingDetails;
import com.example.maidbooking.booking.BookingStatus;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobStatus;

public record BookingResponse(String bookingId, BookingType type, String customerId, String zoneId,
                              String subscriptionId, BookingStatus status, List<JobView> jobs,
                              BigDecimal amountDueNow) {

    public record JobView(String jobId, LocalDateTime start, LocalDateTime end, BigDecimal price, JobStatus status,
                          String maidId, String otp, LocalDateTime holdExpiresAt) {

        static JobView from(Job job) {
            return new JobView(job.id(), job.slot().start(), job.slot().end(), job.price().amount(), job.status(),
                    job.maidId(), job.otp(), job.holdExpiresAt());
        }
    }

    static BookingResponse from(BookingDetails details) {
        Booking booking = details.booking();
        return new BookingResponse(booking.id(), booking.type(), booking.customerId(), booking.zoneId(),
                booking.subscriptionId(), details.status(), details.jobs().stream().map(JobView::from).toList(),
                details.amountDueNow().amount());
    }
}
