package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationService;
import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingRepository;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobRepository;
import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class RecurringChargeJobTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime NOW = MON.minusDays(1).atTime(10, 0);  // Sunday 10:00
    private static final UpiDetails SAVED = new UpiDetails("user@okbank");

    private final JobRepository jobs = new JobRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final SavedPaymentMethods savedMethods = new SavedPaymentMethods(
            new PaymentProcessorRegistry(List.of(new UpiPaymentProcessor(new MockPaymentGateway()))));
    private final PaymentService payments = mock(PaymentService.class);
    private final CapacityService capacity = mock(CapacityService.class);
    private final AllocationService allocation = mock(AllocationService.class);
    private RecurringChargeJob chargeJob;

    @BeforeEach
    void setUp() {
        chargeJob = new RecurringChargeJob(jobs, bookings, savedMethods, payments, capacity, allocation,
                new MutableClock(NOW), 24);
        bookings.save(booking("b-series", "sub-1"));
        bookings.save(booking("b-oneoff", null));
        savedMethods.save("sub-1", SAVED);
    }

    private static Booking booking(String id, String subscriptionId) {
        return new Booking(id, "c-1", "kor-5", "Flat 302", ServiceType.CLEANING,
                subscriptionId == null ? BookingType.SCHEDULED : BookingType.RECURRING, Tier.STANDARD,
                MaidFilter.all(List.of()), null, subscriptionId, NOW.minusDays(3));
    }

    private Job visit(String id, String bookingId, LocalDateTime start, LocalDateTime holdExpiresAt) {
        return jobs.save(new Job(id, bookingId, "kor-5", ServiceType.CLEANING, TimeSlot.of(start, Duration.ofHours(1)),
                Money.of(249), "cr-" + id, "4821", holdExpiresAt));
    }

    @Test
    void chargesOnlyUnpaidSeriesVisitsStartingWithinTheNextDay() {
        visit("j-due", "b-series", MON.atTime(9, 0), null);
        visit("j-later", "b-series", MON.plusDays(1).atTime(9, 0), null);
        visit("j-first", "b-series", MON.atTime(8, 0), NOW.plusMinutes(10));
        visit("j-started", "b-series", NOW.minusHours(1), null);
        visit("j-oneoff", "b-oneoff", MON.atTime(9, 0), null);
        visit("j-paid", "b-series", MON.atTime(10, 0), null).confirm("p-0");

        RecurringChargeResult result = chargeJob.chargeDue();

        assertThat(result).isEqualTo(new RecurringChargeResult(1, 0));
        verify(payments).pay("j-due", SAVED, "j-due:charge");
        verifyNoMoreInteractions(payments);
    }

    @Test
    void aFailedChargeCancelsOnlyThatVisitAndFreesItsCapacityAndMaid() {
        Job declined = visit("j-mon", "b-series", MON.atTime(9, 0), null);
        Job fine = visit("j-mon-pm", "b-series", MON.atTime(9, 30), null);
        given(payments.pay(eq("j-mon"), any(), any())).willThrow(new PaymentFailedException("p-1", "Insufficient funds"));

        RecurringChargeResult result = chargeJob.chargeDue();

        assertThat(result).isEqualTo(new RecurringChargeResult(1, 1));
        assertThat(declined.status()).isEqualTo(JobStatus.CANCELLED);
        verify(capacity).release("cr-j-mon");
        verify(allocation).release("j-mon");
        assertThat(fine.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        verify(payments).pay("j-mon-pm", SAVED, "j-mon-pm:charge");
        verify(capacity, never()).release("cr-j-mon-pm");
    }

    @Test
    void aGatewayOutageLeavesTheVisitForTheNextRun() {
        Job visit = visit("j-mon", "b-series", MON.atTime(9, 0), null);
        given(payments.pay(any(), any(), any())).willThrow(new GatewayUnavailableException("timeout"));

        assertThat(chargeJob.chargeDue()).isEqualTo(new RecurringChargeResult(0, 0));
        assertThat(visit.status()).isEqualTo(JobStatus.PENDING_PAYMENT);
        verify(capacity, never()).release(any());
    }

    @Test
    void aSeriesWithoutASavedMethodHasTheDueVisitCancelled() {
        bookings.save(booking("b-unsaved", "sub-2"));
        Job visit = visit("j-mon", "b-unsaved", MON.atTime(9, 0), null);

        assertThat(chargeJob.chargeDue()).isEqualTo(new RecurringChargeResult(0, 1));
        assertThat(visit.status()).isEqualTo(JobStatus.CANCELLED);
        verify(capacity).release("cr-j-mon");
        verify(payments, never()).pay(any(), any(), any());
    }

    @Test
    void savedMethodsAreCheckedBeforeBeingKept() {
        assertThatThrownBy(() -> savedMethods.save("sub-3", new UpiDetails("nope")))
                .isInstanceOf(ValidationException.class);
        assertThat(savedMethods.find("sub-3")).isEmpty();
        assertThat(savedMethods.find("sub-1")).contains(SAVED);
    }
}
