package com.example.maidbooking.booking.type;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.maidbooking.allocation.DispatchOption;
import com.example.maidbooking.allocation.DispatchStrategy;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.InstantAvailabilityService;
import com.example.maidbooking.discovery.JobDurations;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.zone.ZoneResolver;

/**
 * "Send someone now": one job starting when the nearest idle maid can arrive. The maid is held in the same
 * request ({@link AllocationMode#DISPATCH_NOW}); if nobody can come, the error lists scheduled slots instead.
 */
@Component
public class InstantBookingHandler implements BookingTypeHandler {

    private final ZoneResolver zones;
    private final FilterRegistry filters;
    private final DispatchStrategy dispatch;
    private final InstantAvailabilityService availability;
    private final long promiseMinutes;

    public InstantBookingHandler(ZoneResolver zones, FilterRegistry filters, DispatchStrategy dispatch,
                                 InstantAvailabilityService availability,
                                 @Value("${app.instant.promise-minutes:15}") long promiseMinutes) {
        this.zones = zones;
        this.filters = filters;
        this.dispatch = dispatch;
        this.availability = availability;
        this.promiseMinutes = promiseMinutes;
    }

    @Override
    public BookingType type() {
        return BookingType.INSTANT;
    }

    @Override
    public void validate(CreateBookingRequest request, LocalDateTime now) {
        if (request.durationMinutes() == null) {
            throw new ValidationException("An instant booking needs a duration");
        }
        JobDurations.validate(request.durationMinutes());
    }

    @Override
    public BookingPlan plan(CreateBookingRequest request, LocalDateTime now) {
        String zoneId = zones.resolve(request.locality()).id();
        MaidFilter constraints = MaidFilter.all(filters.fromParams(request.preferences()));
        List<DispatchOption> nearest = dispatch.options(zoneId, request.service(),
                Duration.ofMinutes(request.durationMinutes()), constraints, now);
        if (nearest.isEmpty()) {
            throw new ConflictException(ErrorCode.NO_MAID_NEARBY,
                    "No maid can reach you within " + promiseMinutes + " minutes right now",
                    Map.of("alternatives", availability.alternatives(request.locality(), request.service(),
                            request.durationMinutes(), request.tier(), now.toLocalDate())));
        }
        return BookingPlan.single(nearest.get(0).work());
    }

    @Override
    public AllocationMode allocationMode() {
        return AllocationMode.DISPATCH_NOW;
    }
}
