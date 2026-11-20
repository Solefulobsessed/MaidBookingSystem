package com.example.maidbooking.booking.type;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.booking.Subscription;
import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.JobDurations;
import com.example.maidbooking.zone.ZoneResolver;

/**
 * "Every weekday at 09:00": a subscription plus the visits of its first 14 days, reserved all or nothing.
 * Later visits are generated day by day. Maids are allocated after each visit is paid.
 */
@Component
public class RecurringBookingHandler implements BookingTypeHandler {

    static final int WINDOW_DAYS = 14;
    private static final int SLOT_STEP_MINUTES = 30;

    private final ZoneResolver zones;
    private final Duration minLead;
    private final long maxAdvanceDays;

    public RecurringBookingHandler(ZoneResolver zones, @Value("${app.booking.min-lead-minutes:120}") long minLeadMinutes,
                                   @Value("${app.booking.max-advance-days:30}") long maxAdvanceDays) {
        this.zones = zones;
        this.minLead = Duration.ofMinutes(minLeadMinutes);
        this.maxAdvanceDays = maxAdvanceDays;
    }

    @Override
    public BookingType type() {
        return BookingType.RECURRING;
    }

    @Override
    public void validate(CreateBookingRequest request, LocalDateTime now) {
        RecurrenceRule rule = request.recurrence();
        if (rule == null) {
            throw new ValidationException("A recurring booking needs a recurrence");
        }
        JobDurations.validate((int) rule.duration().toMinutes());
        if (rule.startTime().getMinute() % SLOT_STEP_MINUTES != 0 || rule.startTime().getSecond() != 0) {
            throw new ValidationException("Visits must start on the hour or half hour, was " + rule.startTime());
        }
        LocalDate today = now.toLocalDate();
        if (rule.startDate().isBefore(today) || rule.startDate().isAfter(today.plusDays(maxAdvanceDays))) {
            throw new ValidationException("The series must start between today and " + maxAdvanceDays + " days ahead");
        }
        if (rule.endDate().isBefore(rule.startDate().plusDays(6)) || rule.endDate().isAfter(rule.startDate().plusMonths(6))) {
            throw new ValidationException("A series runs for at least 1 week and at most 6 months");
        }
    }

    @Override
    public BookingPlan plan(CreateBookingRequest request, LocalDateTime now) {
        RecurrenceRule rule = request.recurrence();
        LocalDate windowEnd = rule.startDate().plusDays(WINDOW_DAYS - 1L);
        List<TimeSlot> visits = rule.expand(rule.startDate(), windowEnd).stream()
                .filter(slot -> !slot.start().isBefore(now.plus(minLead)))
                .toList();
        Subscription subscription = new Subscription("sub-" + UUID.randomUUID().toString().substring(0, 8),
                request.customerId(), zones.resolve(request.locality()).id(), request.service(), rule, request.tier(),
                request.preferredMaidId(), windowEnd.isBefore(rule.endDate()) ? windowEnd : rule.endDate());
        return new BookingPlan(visits, subscription);
    }

    @Override
    public AllocationMode allocationMode() {
        return AllocationMode.ALLOCATE_AFTER_PAYMENT;
    }
}
