package com.example.maidbooking.booking;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class JobRepository extends InMemoryRepository<Job, String> {

    private static final Comparator<Job> BY_START = Comparator.comparing(job -> job.slot().start());

    public JobRepository() {
        super("Job", Job::id);
    }

    public List<Job> findByBooking(String bookingId) {
        return findAllMatching(job -> job.bookingId().equals(bookingId)).stream().sorted(BY_START).toList();
    }

    public List<Job> findByStatus(JobStatus status) {
        return findAllMatching(job -> job.status() == status).stream().sorted(BY_START).toList();
    }

    public Optional<Job> findByReservation(String reservationId) {
        return findAllMatching(job -> job.reservationId().equals(reservationId)).stream().findFirst();
    }
}
