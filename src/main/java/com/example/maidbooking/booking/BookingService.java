package com.example.maidbooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.booking.type.AllocationMode;
import com.example.maidbooking.booking.type.BookingPlan;
import com.example.maidbooking.booking.type.BookingTypeHandler;
import com.example.maidbooking.booking.type.BookingTypeRegistry;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneResolver;

/**
 * Creates bookings with one template for every type: handler → validate → plan → price → reserve capacity
 * for every job (all or nothing) → hold a maid now (instant dispatch, or the customer's preferred maid) → save.
 * What differs per type lives in its {@link BookingTypeHandler}.
 */
@Service
public class BookingService {

    private final BookingTypeRegistry types;
    private final ZoneResolver zones;
    private final FilterRegistry filters;
    private final JobReserver reserver;
    private final CapacityService capacity;
    private final AllocationService allocation;
    private final MaidRepository maids;
    private final SubscriptionRepository subscriptions;
    private final BookingRepository bookings;
    private final JobRepository jobs;
    private final Clock clock;
    private final Duration paymentHold;

    public BookingService(BookingTypeRegistry types, ZoneResolver zones, FilterRegistry filters,
                          JobReserver reserver, CapacityService capacity,
                          AllocationService allocation, MaidRepository maids,
                          SubscriptionRepository subscriptions, BookingRepository bookings, JobRepository jobs, Clock clock,
                          @Value("${app.booking.hold-minutes:10}") long holdMinutes) {
        this.types = types;
        this.zones = zones;
        this.filters = filters;
        this.reserver = reserver;
        this.capacity = capacity;
        this.allocation = allocation;
        this.maids = maids;
        this.subscriptions = subscriptions;
        this.bookings = bookings;
        this.jobs = jobs;
        this.clock = clock;
        this.paymentHold = Duration.ofMinutes(holdMinutes);
    }

    public BookingDetails create(CreateBookingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        BookingTypeHandler handler = types.handlerFor(request.type());
        handler.validate(request, now);
        checkPreferredMaid(request);
        Zone zone = zones.resolve(request.locality());
        MaidFilter constraints = MaidFilter.all(filters.fromParams(request.preferences()));
        BookingPlan plan = handler.plan(request, now);

        Subscription subscription = plan.subscription();
        Booking booking = new Booking(newId("b-"), request.customerId(), zone.id(), request.address(),
                request.service(), request.type(), request.tier(), constraints, request.preferredMaidId(),
                subscription == null ? null : subscription.id(), now);
        List<Job> planned = reserveAll(request, handler, plan, zone, booking.id(), now.plus(paymentHold));
        if (handler.allocationMode() == AllocationMode.DISPATCH_NOW) {
            holdMaids(planned, job -> allocation.dispatch(allocationRequest(job, constraints, null), now),
                    () -> new ConflictException(ErrorCode.NO_MAID_NEARBY,
                            "The nearby maid was just booked by someone else; please try again", Map.of()));
        } else if (request.preferredMaidId() != null && subscription == null) {
            String maidId = request.preferredMaidId();
            holdMaids(planned, job -> allocation.allocateOnly(maidId, allocationRequest(job, constraints, maidId),
                            AssignmentStatus.HELD),
                    () -> new ConflictException(ErrorCode.MAID_UNAVAILABLE,
                            "Maid " + maidId + " isn't free at that time; pick another slot or let us choose",
                            Map.of("maidId", maidId)));
        }
        planned.forEach(job -> booking.addJob(job.id()));
        if (subscription != null) {
            subscriptions.save(subscription);
        }
        bookings.save(booking);
        planned.forEach(jobs::save);
        return new BookingDetails(booking, planned);
    }

    public BookingDetails get(String bookingId) {
        Booking booking = bookings.getById(bookingId);
        return new BookingDetails(booking, jobs.findByBooking(bookingId));
    }

    public List<BookingDetails> forCustomer(String customerId) {
        return bookings.findByCustomer(customerId).stream()
                .map(booking -> new BookingDetails(booking, jobs.findByBooking(booking.id()))).toList();
    }

    /**
     * Prices and reserves every planned job. Only the first job (the one paid now) gets the payment hold; later
     * visits of a series are kept until they are charged. If any visit has no room, every reservation is
     * released and the error lists each clashing date.
     */
    private List<Job> reserveAll(CreateBookingRequest request, BookingTypeHandler handler, BookingPlan plan,
                                 Zone zone, String bookingId, LocalDateTime holdUntil) {
        boolean instant = handler.allocationMode() == AllocationMode.DISPATCH_NOW;
        List<Job> planned = new ArrayList<>();
        List<String> clashes = new ArrayList<>();
        ConflictException firstClash = null;
        try {
            for (int i = 0; i < plan.jobSlots().size(); i++) {
                TimeSlot slot = plan.jobSlots().get(i);
                LocalDateTime hold = i == 0 ? holdUntil : null;
                try {
                    planned.add(reserver.reserve(bookingId, zone.id(), request.service(), request.tier(), slot, hold,
                            instant));
                } catch (ConflictException clash) {
                    firstClash = firstClash == null ? clash : firstClash;
                    clashes.add(slot.start().toLocalDate().toString());
                }
            }
        } catch (RuntimeException e) {
            planned.forEach(job -> capacity.release(job.reservationId()));
            throw e;
        }
        if (firstClash != null) {
            planned.forEach(job -> capacity.release(job.reservationId()));
            throw plan.jobSlots().size() == 1 ? firstClash : new ConflictException(ErrorCode.SLOT_UNAVAILABLE,
                    "No capacity on " + String.join(", ", clashes) + "; nothing was booked",
                    Map.of("conflictDates", clashes));
        }
        return planned;
    }

    /** A preferred maid must exist, offer the service and be of the booked tier (her price is the tier's). */
    private void checkPreferredMaid(CreateBookingRequest request) {
        if (request.preferredMaidId() == null) {
            return;
        }
        Maid maid = maids.getById(request.preferredMaidId());
        if (!maid.offers(request.service()) || maid.tier() != request.tier()) {
            throw new ValidationException("Maid " + maid.id() + " doesn't offer " + request.service() + " at the "
                    + request.tier() + " tier");
        }
    }

    private static AllocationRequest allocationRequest(Job job, MaidFilter constraints, String preferredMaidId) {
        return new AllocationRequest(job.id(), job.zoneId(), job.service(), job.slot(), constraints, preferredMaidId,
                null);
    }

    /** Holds a maid for each job right away; if one can't be held, every hold and reservation is undone. */
    private void holdMaids(List<Job> planned, Function<Job, Optional<Assignment>> hold,
                           Supplier<ConflictException> failure) {
        try {
            for (Job job : planned) {
                Assignment held = hold.apply(job).orElseThrow(failure);
                job.holdMaid(held.maidId(), held.id());
            }
        } catch (RuntimeException e) {
            planned.forEach(job -> {
                allocation.release(job.id());
                capacity.release(job.reservationId());
            });
            throw e;
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
