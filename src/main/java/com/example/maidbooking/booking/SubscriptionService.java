package com.example.maidbooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;

/**
 * Keeps every ACTIVE subscription's jobs 14 days ahead: a daily run adds the next day's visit. Each new visit
 * reserves capacity and is offered first to the maid of the series' latest visit (then anyone). A day without
 * capacity is recorded as SKIPPED_NO_CAPACITY; the series carries on.
 */
@Service
public class SubscriptionService {

    static final int WINDOW_DAYS = 14;
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final BookingRepository bookings;
    private final JobRepository jobs;
    private final JobReserver reserver;
    private final AllocationService allocation;
    private final Clock clock;
    private final Duration minLead;

    public SubscriptionService(SubscriptionRepository subscriptions, BookingRepository bookings, JobRepository jobs,
                               JobReserver reserver, AllocationService allocation, Clock clock,
                               @Value("${app.booking.min-lead-minutes:120}") long minLeadMinutes) {
        this.subscriptions = subscriptions;
        this.bookings = bookings;
        this.jobs = jobs;
        this.reserver = reserver;
        this.allocation = allocation;
        this.clock = clock;
        this.minLead = Duration.ofMinutes(minLeadMinutes);
    }

    public SubscriptionGenerationResult generate() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate windowEnd = now.toLocalDate().plusDays(WINDOW_DAYS - 1L);
        int created = 0;
        int skipped = 0;
        for (Subscription subscription : subscriptions.findByStatus(SubscriptionStatus.ACTIVE)) {
            LocalDate until = windowEnd.isBefore(subscription.rule().endDate()) ? windowEnd : subscription.rule().endDate();
            if (!until.isAfter(subscription.generatedUntil())) {
                continue;
            }
            Booking booking = bookings.findBySubscription(subscription.id()).orElseThrow();
            for (TimeSlot visit : subscription.rule().expand(subscription.generatedUntil().plusDays(1), until)) {
                if (visit.start().isBefore(now.plus(minLead))) {
                    continue;
                }
                try {
                    Job job = reserver.reserve(booking.id(), subscription.zoneId(), subscription.service(),
                            subscription.tier(), visit, null, false);
                    holdUsualMaid(booking, subscription, job);
                    booking.addJob(job.id());
                    jobs.save(job);
                    created++;
                } catch (ConflictException noRoom) {
                    subscription.skipNoCapacity(visit.start().toLocalDate());
                    skipped++;
                    log.info("Subscription {}: no capacity on {}, visit skipped; customer {} to be notified",
                            subscription.id(), visit.start().toLocalDate(), subscription.customerId());
                }
            }
            subscription.extendTo(until);
        }
        return new SubscriptionGenerationResult(created, skipped);
    }

    /** Continuity: the maid of the latest earlier visit (else the customer's preferred maid) scores highest. */
    private void holdUsualMaid(Booking booking, Subscription subscription, Job job) {
        String usualMaid = jobs.findByBooking(booking.id()).stream()
                .filter(earlier -> earlier.maidId() != null && earlier.slot().start().isBefore(job.slot().start()))
                .reduce((first, second) -> second)
                .map(Job::maidId)
                .orElse(subscription.preferredMaidId());
        allocation.allocate(new AllocationRequest(job.id(), subscription.zoneId(), subscription.service(), job.slot(),
                        booking.constraints(), usualMaid, null), AssignmentStatus.HELD)
                .ifPresent(held -> job.holdMaid(held.maidId(), held.id()));
    }
}
