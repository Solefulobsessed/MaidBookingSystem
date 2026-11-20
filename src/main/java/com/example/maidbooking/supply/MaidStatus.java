package com.example.maidbooking.supply;

/** Only ACTIVE maids get shifts, appear in search and receive jobs. */
public enum MaidStatus {
    ONBOARDING,
    ACTIVE,
    SUSPENDED
}
