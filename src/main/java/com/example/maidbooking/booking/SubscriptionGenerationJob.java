package com.example.maidbooking.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs subscription generation once a day, shortly after midnight. */
@Component
public class SubscriptionGenerationJob {

    private final SubscriptionService subscriptionService;

    public SubscriptionGenerationJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "${app.subscription.generation-cron:0 30 0 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
    public void run() {
        subscriptionService.generate();
    }
}
