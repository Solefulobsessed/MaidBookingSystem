package com.example.maidbooking.booking;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class SubscriptionRepository extends InMemoryRepository<Subscription, String> {

    public SubscriptionRepository() {
        super("Subscription", Subscription::id);
    }

    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return findAllMatching(subscription -> subscription.status() == status);
    }
}
