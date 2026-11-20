package com.example.maidbooking.booking;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class SubscriptionRepository extends InMemoryRepository<Subscription, String> {

    public SubscriptionRepository() {
        super("Subscription", Subscription::id);
    }
}
