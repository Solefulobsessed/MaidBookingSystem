package com.example.maidbooking.booking;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class BookingRepository extends InMemoryRepository<Booking, String> {

    public BookingRepository() {
        super("Booking", Booking::id);
    }

    /** Newest first. */
    public List<Booking> findByCustomer(String customerId) {
        return findAllMatching(b -> b.customerId().equals(customerId)).stream()
                .sorted(Comparator.comparing(Booking::createdAt).reversed()).toList();
    }
}
