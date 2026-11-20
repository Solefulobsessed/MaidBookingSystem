package com.example.maidbooking.booking;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;

/**
 * One visit by one maid in one slot: the unit that is reserved, paid, assigned, cancelled and refunded.
 * Status changes only through the methods below, which follow {@link JobStatus}'s table.
 */
public class Job {

    private final String id;
    private final String bookingId;
    private final String zoneId;
    private final ServiceType service;
    private final TimeSlot slot;
    private final Money price;
    private final String otp;
    private final LocalDateTime holdExpiresAt;
    private final String reservationId;
    private JobStatus status = JobStatus.PENDING_PAYMENT;
    private String maidId;
    private String assignmentId;
    private String paymentId;
    private final Set<String> droppedBy = new LinkedHashSet<>();

    public Job(String id, String bookingId, String zoneId, ServiceType service, TimeSlot slot, Money price,
               String reservationId, String otp, LocalDateTime holdExpiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.service = Objects.requireNonNull(service, "service");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.price = Objects.requireNonNull(price, "price");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        if (otp == null || !otp.matches("\\d{4}")) {
            throw new ValidationException("OTP must be 4 digits");
        }
        this.otp = otp;
        this.holdExpiresAt = holdExpiresAt;
    }

    /** An instant job keeps its maid on hold while awaiting payment; {@link #confirm} then assigns her. */
    public void holdMaid(String maidId, String assignmentId) {
        require(status == JobStatus.PENDING_PAYMENT, "hold a maid for");
        this.maidId = Objects.requireNonNull(maidId);
        this.assignmentId = Objects.requireNonNull(assignmentId);
    }

    /** Payment arrived. Only an unpaid job can be confirmed (ASSIGNED → CONFIRMED is the drop-out path). */
    public void confirm(String paymentId) {
        require(status == JobStatus.PENDING_PAYMENT, "confirm");
        move(JobStatus.CONFIRMED, "confirm");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        if (assignmentId != null) {
            move(JobStatus.ASSIGNED, "assign");
        }
    }

    public void assign(String maidId, String assignmentId) {
        move(JobStatus.ASSIGNED, "assign");
        this.maidId = Objects.requireNonNull(maidId);
        this.assignmentId = Objects.requireNonNull(assignmentId);
    }

    /** The maid dropped out: back to waiting for a maid, keeping the payment and the capacity. */
    public void unassign() {
        require(status == JobStatus.ASSIGNED, "unassign");
        move(JobStatus.CONFIRMED, "unassign");
        forgetMaid();
    }

    /** The maid held for an unpaid job can't come; a maid is found again when it is paid. */
    public void releaseHeldMaid() {
        require(status == JobStatus.PENDING_PAYMENT && assignmentId != null, "release the held maid of");
        forgetMaid();
    }

    /** A maid who dropped this job is never offered it again. */
    private void forgetMaid() {
        droppedBy.add(maidId);
        maidId = null;
        assignmentId = null;
    }

    public void markUnfulfilled() {
        move(JobStatus.UNFULFILLED, "mark unfulfilled");
    }

    public void expire() {
        move(JobStatus.EXPIRED, "expire");
    }

    public void cancel() {
        move(JobStatus.CANCELLED, "cancel");
    }

    public void enRoute() {
        move(JobStatus.EN_ROUTE, "set off for");
    }

    public void start(String enteredOtp) {
        require(status == JobStatus.EN_ROUTE, "start");
        if (!otp.equals(enteredOtp)) {
            throw new ValidationException("Wrong OTP for job " + id);
        }
        move(JobStatus.IN_PROGRESS, "start");
    }

    public void complete() {
        move(JobStatus.COMPLETED, "complete");
    }

    private void move(JobStatus target, String action) {
        require(status.canMoveTo(target), action);
        status = target;
    }

    private void require(boolean allowed, String action) {
        if (!allowed) {
            throw new InvalidStateTransitionException("Job", status, action);
        }
    }

    public String id() { return id; }
    public String bookingId() { return bookingId; }
    public String zoneId() { return zoneId; }
    public ServiceType service() { return service; }
    public TimeSlot slot() { return slot; }
    public Money price() { return price; }
    public String otp() { return otp; }
    public LocalDateTime holdExpiresAt() { return holdExpiresAt; }
    public String reservationId() { return reservationId; }
    public JobStatus status() { return status; }
    public String maidId() { return maidId; }
    public String assignmentId() { return assignmentId; }
    public String paymentId() { return paymentId; }
    public Set<String> droppedBy() { return Set.copyOf(droppedBy); }
}
