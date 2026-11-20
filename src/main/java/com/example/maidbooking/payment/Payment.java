package com.example.maidbooking.payment;

import java.util.Objects;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.InvalidStateTransitionException;

/**
 * One attempt to pay for one job. INITIATED → SUCCEEDED | FAILED; a succeeded payment can then be refunded in
 * parts (PARTIALLY_REFUNDED) until nothing is left (REFUNDED).
 */
public class Payment {

    private final String id;
    private final String jobId;
    private final Money amount;
    private final PaymentMethodType method;
    private final String idempotencyKey;
    private PaymentStatus status = PaymentStatus.INITIATED;
    private String gatewayRef;
    private Money refundedAmount = Money.ZERO;

    public Payment(String id, String jobId, Money amount, PaymentMethodType method, String idempotencyKey) {
        this.id = Objects.requireNonNull(id, "id");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.method = Objects.requireNonNull(method, "method");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    public void succeed(String gatewayRef) {
        require(status == PaymentStatus.INITIATED, "succeed");
        this.gatewayRef = Objects.requireNonNull(gatewayRef, "gatewayRef");
        status = PaymentStatus.SUCCEEDED;
    }

    public void fail() {
        require(status == PaymentStatus.INITIATED, "fail");
        status = PaymentStatus.FAILED;
    }

    /** Books a refund the gateway approved; the total refunded can never exceed the amount paid. */
    public void recordRefund(Money refund) {
        require(status == PaymentStatus.SUCCEEDED || status == PaymentStatus.PARTIALLY_REFUNDED, "refund");
        if (refund.isZero() || refund.compareTo(refundable()) > 0) {
            throw new IllegalArgumentException("Refund " + refund + " must be positive and at most " + refundable());
        }
        refundedAmount = refundedAmount.plus(refund);
        status = refundedAmount.equals(amount) ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }

    /** What can still be refunded; only meaningful once the payment succeeded. */
    public Money refundable() {
        return amount.minus(refundedAmount);
    }

    private void require(boolean allowed, String action) {
        if (!allowed) {
            throw new InvalidStateTransitionException("Payment", status, action);
        }
    }

    public String id() { return id; }
    public String jobId() { return jobId; }
    public Money amount() { return amount; }
    public PaymentMethodType method() { return method; }
    public String idempotencyKey() { return idempotencyKey; }
    public PaymentStatus status() { return status; }
    public String gatewayRef() { return gatewayRef; }
    public Money refundedAmount() { return refundedAmount; }
}
