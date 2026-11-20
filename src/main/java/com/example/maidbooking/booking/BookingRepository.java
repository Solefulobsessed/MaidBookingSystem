package com.example.maidbooking.booking;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class BookingRepository extends InMemoryRepository<Booking, String> {

    public BookingRepository() {
        super("Booking", Booking::id);
    }

    public Optional<Booking> findBySubscription(String subscriptionId) {
        return findAllMatching(b -> subscriptionId.equals(b.subscriptionId())).stream().findFirst();
    }

    /** Newest first. */
    public List<Booking> findByCustomer(String customerId) {
        return findAllMatching(b -> b.customerId().equals(customerId)).stream()
                .sorted(Comparator.comparing(Booking::createdAt).reversed()).toList();
    }
}
