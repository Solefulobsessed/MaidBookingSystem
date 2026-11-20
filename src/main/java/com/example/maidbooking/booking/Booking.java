package com.example.maidbooking.booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/**
 * The customer's order. Holds one job (instant, scheduled) or the jobs a subscription has generated
 * (recurring). The customer's maid requirements are kept so every job, and every retry, honours them.
 */
public class Booking {

    private final String id;
    private final String customerId;
    private final String zoneId;
    private final String address;
    private final ServiceType service;
    private final BookingType type;
    private final Tier tier;
    private final MaidFilter constraints;
    private final String preferredMaidId;
    private final String subscriptionId;
    private final LocalDateTime createdAt;
    private final List<String> jobIds = new ArrayList<>();

    public Booking(String id, String customerId, String zoneId, String address, ServiceType service, BookingType type,
                   Tier tier, MaidFilter constraints, String preferredMaidId, String subscriptionId,
                   LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.address = Objects.requireNonNull(address, "address");
        this.service = Objects.requireNonNull(service, "service");
        this.type = Objects.requireNonNull(type, "type");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.constraints = Objects.requireNonNull(constraints, "constraints");
        this.preferredMaidId = preferredMaidId;
        this.subscriptionId = subscriptionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void addJob(String jobId) {
        if (jobIds.contains(jobId)) {
            throw new IllegalArgumentException("Job " + jobId + " is already part of booking " + id);
        }
        jobIds.add(jobId);
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public String zoneId() { return zoneId; }
    public String address() { return address; }
    public ServiceType service() { return service; }
    public BookingType type() { return type; }
    public Tier tier() { return tier; }
    public MaidFilter constraints() { return constraints; }
    public String preferredMaidId() { return preferredMaidId; }
    public String subscriptionId() { return subscriptionId; }
    public LocalDateTime createdAt() { return createdAt; }
    public List<String> jobIds() { return List.copyOf(jobIds); }
}
