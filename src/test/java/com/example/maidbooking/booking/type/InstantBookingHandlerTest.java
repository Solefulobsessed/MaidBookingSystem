package com.example.maidbooking.booking.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.Candidate;
import com.example.maidbooking.allocation.DispatchOption;
import com.example.maidbooking.allocation.DispatchStrategy;
import com.example.maidbooking.allocation.MaidCalendar;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.InstantAvailability;
import com.example.maidbooking.discovery.InstantAvailabilityService;
import com.example.maidbooking.discovery.SlotStatus;
import com.example.maidbooking.discovery.filter.FilterRegistry;
import com.example.maidbooking.discovery.filter.StandardFilters;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.LocalityZoneResolver;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

class InstantBookingHandlerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 9, 2);

    private final InstantAvailabilityService availability = mock(InstantAvailabilityService.class);
    private final List<DispatchOption> options = new ArrayList<>();
    private final List<String> askedFor = new ArrayList<>();
    private final DispatchStrategy dispatch = (zoneId, service, duration, constraints, now) -> {
        askedFor.add(zoneId + " " + service + " " + duration.toMinutes() + " " + constraints.describe());
        return options;
    };
    private final InstantBookingHandler handler;

    InstantBookingHandlerTest() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        StandardFilters standard = new StandardFilters();
        handler = new InstantBookingHandler(new LocalityZoneResolver(zones),
                new FilterRegistry(List.of(standard.genderFilter())), dispatch, availability, 15);
    }

    private static CreateBookingRequest instant(Integer minutes) {
        return new CreateBookingRequest(BookingType.INSTANT, "c-1", "Koramangala", "Flat 302", ServiceType.CLEANING,
                null, null, minutes, Map.of("gender", "FEMALE"), null);
    }

    private static DispatchOption arrivingAt(int hour, int minute) {
        Maid maid = new Maid("m-01", "Asha", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING), null,
                Tier.STANDARD, "kor-5", 4.5);
        Shift shift = new Shift("s-1", "m-01", "kor-5",
                new TimeSlot(NOW.toLocalDate().atTime(7, 0), NOW.toLocalDate().atTime(13, 0)));
        TimeSlot work = TimeSlot.of(NOW.toLocalDate().atTime(hour, minute), Duration.ofMinutes(60));
        return new DispatchOption(new Candidate(maid, shift, new MaidCalendar("m-01", List.of()), work), work,
                Duration.between(NOW, work.start()));
    }

    @Test
    void plansOneJobStartingWhenTheNearestMaidArrives() {
        options.add(arrivingAt(9, 15));
        options.add(arrivingAt(9, 35));

        BookingPlan plan = handler.plan(instant(60), NOW);

        assertThat(plan.jobSlots()).containsExactly(TimeSlot.of(NOW.withMinute(15), Duration.ofMinutes(60)));
        assertThat(askedFor).containsExactly("kor-5 CLEANING 60 gender=FEMALE");
        assertThat(handler.allocationMode()).isEqualTo(AllocationMode.DISPATCH_NOW);
        assertThat(handler.type()).isEqualTo(BookingType.INSTANT);
    }

    @Test
    void nobodyNearbyIsNoMaidNearbyWithScheduledAlternatives() {
        InstantAvailability.Alternative later = new InstantAvailability.Alternative(NOW.withHour(14).withMinute(0),
                SlotStatus.OPEN, Money.of(249));
        given(availability.alternatives(eq("Koramangala"), eq(ServiceType.CLEANING), anyInt(), eq(Tier.STANDARD), any()))
                .willReturn(List.of(later));

        assertThatThrownBy(() -> handler.plan(instant(60), NOW))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NO_MAID_NEARBY);
                    assertThat(e.getMessage()).contains("15 minutes");
                    assertThat(e.details().get("alternatives")).isEqualTo(List.of(later));
                });
    }

    @Test
    void needsAValidDuration() {
        handler.validate(instant(60), NOW);
        assertThatThrownBy(() -> handler.validate(instant(null), NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a duration");
        assertThatThrownBy(() -> handler.validate(instant(45), NOW)).isInstanceOf(ValidationException.class);
    }

    @Test
    void cannotChooseTheMaid() {
        CreateBookingRequest withMaid = new CreateBookingRequest(BookingType.INSTANT, "c-1", "Koramangala", "Flat 302",
                ServiceType.CLEANING, null, null, 60, null, "m-7f3a");

        assertThatThrownBy(() -> handler.validate(withMaid, NOW))
                .isInstanceOf(ValidationException.class).hasMessageContaining("scheduled slot");
    }
}
