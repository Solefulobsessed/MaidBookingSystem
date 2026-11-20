package com.example.maidbooking.booking.type;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.JobDurations;

/** One job at the start the customer picked, 2 hours to 30 days ahead; the maid is allocated after payment. */
@Component
public class ScheduledBookingHandler implements BookingTypeHandler {

    private static final int SLOT_STEP_MINUTES = 30;

    private final Duration minLead;
    private final Duration maxAdvance;

    public ScheduledBookingHandler(@Value("${app.booking.min-lead-minutes:120}") long minLeadMinutes,
                                   @Value("${app.booking.max-advance-days:30}") long maxAdvanceDays) {
        this.minLead = Duration.ofMinutes(minLeadMinutes);
        this.maxAdvance = Duration.ofDays(maxAdvanceDays);
    }

    @Override
    public BookingType type() {
        return BookingType.SCHEDULED;
    }

    @Override
    public void validate(CreateBookingRequest request, LocalDateTime now) {
        LocalDateTime start = request.start();
        if (start == null || request.durationMinutes() == null) {
            throw new ValidationException("A scheduled booking needs a start and a duration");
        }
        JobDurations.validate(request.durationMinutes());
        if (start.getMinute() % SLOT_STEP_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw new ValidationException("Start must be on the hour or half hour, was " + start);
        }
        if (start.isBefore(now.plus(minLead))) {
            throw new ValidationException("Start must be at least " + minLead.toMinutes() + " minutes from now");
        }
        if (start.isAfter(now.plus(maxAdvance))) {
            throw new ValidationException("Start must be within " + maxAdvance.toDays() + " days from now");
        }
        if (slot(request).end().isAfter(start.toLocalDate().plusDays(1).atStartOfDay())) {
            throw new ValidationException("A job can't run past midnight");
        }
    }

    @Override
    public BookingPlan plan(CreateBookingRequest request, LocalDateTime now) {
        return BookingPlan.single(slot(request));
    }

    @Override
    public AllocationMode allocationMode() {
        return AllocationMode.ALLOCATE_AFTER_PAYMENT;
    }

    private static TimeSlot slot(CreateBookingRequest request) {
        return TimeSlot.of(request.start(), Duration.ofMinutes(request.durationMinutes()));
    }
}
