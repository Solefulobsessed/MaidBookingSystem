package com.example.maidbooking.booking;

import java.util.List;

import com.example.maidbooking.common.Money;

/** A booking with its jobs in time order, as returned to the customer. */
public record BookingDetails(Booking booking, List<Job> jobs) {

    public BookingDetails {
        jobs = List.copyOf(jobs);
    }

    public BookingStatus status() {
        return BookingStatus.of(jobs.stream().map(Job::status).toList());
    }

    /** The price of the earliest job still awaiting payment; zero when nothing is due. */
    public Money amountDueNow() {
        return jobs.stream().filter(job -> job.status() == JobStatus.PENDING_PAYMENT)
                .findFirst().map(Job::price).orElse(Money.ZERO);
    }
}
