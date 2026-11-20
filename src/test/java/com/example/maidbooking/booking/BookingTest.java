package com.example.maidbooking.booking;

import static com.example.maidbooking.booking.JobStatus.ASSIGNED;
import static com.example.maidbooking.booking.JobStatus.CANCELLED;
import static com.example.maidbooking.booking.JobStatus.COMPLETED;
import static com.example.maidbooking.booking.JobStatus.CONFIRMED;
import static com.example.maidbooking.booking.JobStatus.EXPIRED;
import static com.example.maidbooking.booking.JobStatus.PENDING_PAYMENT;
import static com.example.maidbooking.booking.JobStatus.UNFULFILLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class BookingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 18, 0);

    static Booking booking(String id, String customerId, LocalDateTime createdAt) {
        return new Booking(id, customerId, "kor-5", "Flat 302", ServiceType.CLEANING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), null, null, createdAt);
    }

    @Test
    void statusIsDerivedFromTheJobs() {
        assertThat(BookingStatus.of(List.of(PENDING_PAYMENT))).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(BookingStatus.of(List.of(CONFIRMED))).isEqualTo(BookingStatus.ACTIVE);
        assertThat(BookingStatus.of(List.of(COMPLETED))).isEqualTo(BookingStatus.COMPLETED);
        assertThat(BookingStatus.of(List.of(EXPIRED))).isEqualTo(BookingStatus.CANCELLED);
        assertThat(BookingStatus.of(List.of(UNFULFILLED))).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void recurringSeriesStatus() {
        assertThat(BookingStatus.of(List.of(ASSIGNED, PENDING_PAYMENT, PENDING_PAYMENT)))
                .as("first visit paid, later ones not charged yet").isEqualTo(BookingStatus.ACTIVE);
        assertThat(BookingStatus.of(List.of(COMPLETED, PENDING_PAYMENT)))
                .as("past visit done, next one open").isEqualTo(BookingStatus.ACTIVE);
        assertThat(BookingStatus.of(List.of(COMPLETED, CANCELLED, CANCELLED)))
                .as("series cancelled after one visit").isEqualTo(BookingStatus.COMPLETED);
        assertThat(BookingStatus.of(List.of(CANCELLED, EXPIRED))).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void jobsAreAddedOnceAndTheListCannotBeChangedFromOutside() {
        Booking booking = booking("b-1", "c-1", NOW);
        booking.addJob("j-1");
        booking.addJob("j-2");

        assertThat(booking.jobIds()).containsExactly("j-1", "j-2");
        assertThatThrownBy(() -> booking.addJob("j-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> booking.jobIds().add("j-3")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void customerBookingsNewestFirst() {
        BookingRepository repo = new BookingRepository();
        repo.save(booking("b-old", "c-1", NOW.minusDays(2)));
        repo.save(booking("b-new", "c-1", NOW));
        repo.save(booking("b-other", "c-2", NOW));

        assertThat(repo.findByCustomer("c-1")).extracting(Booking::id).containsExactly("b-new", "b-old");
    }

    @Test
    void jobQueries() {
        JobRepository repo = new JobRepository();
        Job later = job("j-later", "b-1", "cr-2", 11);
        Job earlier = job("j-earlier", "b-1", "cr-1", 9);
        repo.save(later);
        repo.save(earlier);
        repo.save(job("j-other", "b-2", "cr-3", 9));
        earlier.confirm("p-1");

        assertThat(repo.findByBooking("b-1")).extracting(Job::id).containsExactly("j-earlier", "j-later");
        assertThat(repo.findByStatus(JobStatus.CONFIRMED)).extracting(Job::id).containsExactly("j-earlier");
        assertThat(repo.findByReservation("cr-2")).contains(later);
        assertThat(repo.findByReservation("cr-x")).isEmpty();
    }

    private static Job job(String id, String bookingId, String reservationId, int hour) {
        return new Job(id, bookingId, "kor-5", ServiceType.CLEANING,
                TimeSlot.of(LocalDate.of(2026, 11, 23).atTime(hour, 0), Duration.ofHours(1)), Money.of(249),
                reservationId, "4821", null);
    }
}
