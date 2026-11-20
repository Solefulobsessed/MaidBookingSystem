package com.example.maidbooking.allocation;

import java.util.Objects;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

/**
 * One maid booked for one job. {@code blocked} is the travel buffer before the job plus the job itself;
 * it is the time no other job of hers may overlap.
 */
public class Assignment {

    private final String id;
    private final String referenceId;
    private final String maidId;
    private final String shiftId;
    private final String zoneId;
    private final TimeSlot work;
    private final TimeSlot blocked;
    private AssignmentStatus status;

    public Assignment(String id, String referenceId, String maidId, String shiftId, String zoneId,
                      TimeSlot work, TimeSlot blocked, AssignmentStatus initialStatus) {
        this.id = Objects.requireNonNull(id, "id");
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId");
        this.maidId = Objects.requireNonNull(maidId, "maidId");
        this.shiftId = Objects.requireNonNull(shiftId, "shiftId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.work = Objects.requireNonNull(work, "work");
        this.blocked = Objects.requireNonNull(blocked, "blocked");
        if (!blocked.end().equals(work.end()) || !blocked.contains(work)) {
            throw new ValidationException("Blocked time must be the travel buffer followed by the job");
        }
        if (initialStatus == AssignmentStatus.RELEASED) {
            throw new ValidationException("A new assignment cannot start RELEASED");
        }
        this.status = initialStatus;
    }

    /** A held instant assignment becomes active once the job is paid for. */
    public void activate() {
        if (status != AssignmentStatus.HELD) {
            throw new InvalidStateTransitionException("Assignment", status, "activate");
        }
        status = AssignmentStatus.ACTIVE;
    }

    public void release() {
        if (status == AssignmentStatus.RELEASED) {
            throw new InvalidStateTransitionException("Assignment", status, "release");
        }
        status = AssignmentStatus.RELEASED;
    }

    public boolean isLive() {
        return status.isLive();
    }

    public String id() { return id; }
    public String referenceId() { return referenceId; }
    public String maidId() { return maidId; }
    public String shiftId() { return shiftId; }
    public String zoneId() { return zoneId; }
    public TimeSlot work() { return work; }
    public TimeSlot blocked() { return blocked; }
    public AssignmentStatus status() { return status; }
}
