package com.example.maidbooking.booking;

import java.time.LocalDate;
import java.util.Objects;

import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/**
 * A recurring booking's standing order. Jobs exist up to {@code generatedUntil}; later ones are generated day by
 * day while the subscription is ACTIVE. The preferred maid is tried first for every visit, but is not required.
 */
public class Subscription {

    private final String id;
    private final String customerId;
    private final String zoneId;
    private final ServiceType service;
    private final RecurrenceRule rule;
    private final Tier tier;
    private final String preferredMaidId;
    private LocalDate generatedUntil;
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    public Subscription(String id, String customerId, String zoneId, ServiceType service, RecurrenceRule rule,
                        Tier tier, String preferredMaidId, LocalDate generatedUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.service = Objects.requireNonNull(service, "service");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.preferredMaidId = preferredMaidId;
        this.generatedUntil = Objects.requireNonNull(generatedUntil, "generatedUntil");
    }

    public void pause() {
        require(SubscriptionStatus.ACTIVE, "pause");
        status = SubscriptionStatus.PAUSED;
    }

    public void resume() {
        require(SubscriptionStatus.PAUSED, "resume");
        status = SubscriptionStatus.ACTIVE;
    }

    public void cancel() {
        if (status == SubscriptionStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Subscription", status, "cancel");
        }
        status = SubscriptionStatus.CANCELLED;
    }

    private void require(SubscriptionStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateTransitionException("Subscription", status, action);
        }
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public String zoneId() { return zoneId; }
    public ServiceType service() { return service; }
    public RecurrenceRule rule() { return rule; }
    public Tier tier() { return tier; }
    public String preferredMaidId() { return preferredMaidId; }
    public LocalDate generatedUntil() { return generatedUntil; }
    public SubscriptionStatus status() { return status; }
}
