package com.example.maidbooking.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.booking.type.AllocationMode;
import com.example.maidbooking.booking.type.BookingPlan;
import com.example.maidbooking.booking.type.BookingTypeHandler;
import com.example.maidbooking.booking.type.BookingTypeRegistry;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.zone.TravelTimeEstimator;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneResolver;

/**
 * Creates bookings with one template for every type: handler → validate → plan → price → reserve capacity
 * for every job (all or nothing) → save. What differs per type lives in its {@link BookingTypeHandler}.
 */
@Service
public class BookingService {

    private final BookingTypeRegistry types;
    private final ZoneResolver zones;
    private final TravelTimeEstimator travel;
    private final FilterRegistry filters;
    private final PricingStrategy pricing;
    private final CapacityService capacity;
    private final BookingRepository bookings;
    private final JobRepository jobs;
    private final Clock clock;
    private final Duration paymentHold;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingTypeRegistry types, ZoneResolver zones, TravelTimeEstimator travel,
                          FilterRegistry filters, PricingStrategy pricing, CapacityService capacity,
                          BookingRepository bookings, JobRepository jobs, Clock clock,
                          @Value("${app.booking.hold-minutes:10}") long holdMinutes) {
        this.types = types;
        this.zones = zones;
        this.travel = travel;
        this.filters = filters;
        this.pricing = pricing;
        this.capacity = capacity;
        this.bookings = bookings;
        this.jobs = jobs;
        this.clock = clock;
        this.paymentHold = Duration.ofMinutes(holdMinutes);
    }

    public BookingDetails create(CreateBookingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        BookingTypeHandler handler = types.handlerFor(request.type());
        handler.validate(request, now);
        Zone zone = zones.resolve(request.locality());
        MaidFilter constraints = MaidFilter.all(filters.fromParams(request.preferences()));
        BookingPlan plan = handler.plan(request, now);

        Booking booking = new Booking(newId("b-"), request.customerId(), zone.id(), request.address(),
                request.service(), request.type(), request.tier(), constraints, request.preferredMaidId(), null, now);
        List<Job> planned = reserveAll(request, handler, plan, zone, booking.id(), now.plus(paymentHold));
        planned.forEach(job -> booking.addJob(job.id()));
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

    /** Prices and reserves every planned job; if any reservation fails, the ones already made are released. */
    private List<Job> reserveAll(CreateBookingRequest request, BookingTypeHandler handler, BookingPlan plan,
                                 Zone zone, String bookingId, LocalDateTime holdUntil) {
        Duration buffer = travel.estimate(zone, zone).orElseThrow();
        boolean instant = handler.allocationMode() == AllocationMode.DISPATCH_NOW;
        List<Job> planned = new ArrayList<>();
        try {
            for (TimeSlot slot : plan.jobSlots()) {
                String jobId = newId("j-");
                Money price = pricing.price(new PriceQuery(request.service(), request.tier(), slot, zone.id(), instant));
                String reservationId = capacity.reserve(zone.id(), request.service(), slot.withBufferBefore(buffer),
                        jobId, holdUntil).id();
                planned.add(new Job(jobId, bookingId, zone.id(), request.service(), slot, price, reservationId,
                        "%04d".formatted(random.nextInt(10_000)), holdUntil));
            }
        } catch (RuntimeException e) {
            planned.forEach(job -> capacity.release(job.reservationId()));
            throw e;
        }
        return planned;
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
