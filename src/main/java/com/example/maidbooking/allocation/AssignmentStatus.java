package com.example.maidbooking.allocation;

/** HELD while an instant job awaits payment, ACTIVE once the job is confirmed, RELEASED when freed. */
public enum AssignmentStatus {
    HELD,
    ACTIVE,
    RELEASED;

    public boolean isLive() {
        return this != RELEASED;
    }
}
