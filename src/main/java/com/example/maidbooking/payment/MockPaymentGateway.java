package com.example.maidbooking.payment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.Money;

/**
 * In-memory stand-in for a real provider. Approves by default; an instrument ending in {@code decline}
 * (e.g. {@code UPI:decline}) is declined, which makes failures easy to demo. Tests can script the next calls
 * with {@link #declineNext} and {@link #failNext}. Results are remembered per idempotency key.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private record Seen(Object request, GatewayResult result) {
    }

    /** A scripted outcome for the next call: declined with a reason, or an outage when the reason is null. */
    private record Scripted(String declineReason) {
    }

    private final Map<String, Seen> byKey = new HashMap<>();
    private final Map<String, Money> refundableByCharge = new HashMap<>();
    private final Deque<Scripted> scripted = new ArrayDeque<>();
    private int moneyMovements;

    /** The next call (charge or refund) is declined with {@code reason}. */
    public synchronized void declineNext(String reason) {
        scripted.add(new Scripted(Objects.requireNonNull(reason, "reason")));
    }

    /** The next call throws {@link GatewayUnavailableException}. */
    public synchronized void failNext() {
        scripted.add(new Scripted(null));
    }

    /** How many charges and refunds actually moved money (replays don't count). */
    public synchronized int moneyMovements() {
        return moneyMovements;
    }

    @Override
    public synchronized GatewayResult charge(GatewayChargeRequest request) {
        return once(request.idempotencyKey(), request, () -> {
            if (request.instrument().endsWith("decline")) {
                return GatewayResult.declined("Declined by issuer");
            }
            String ref = "pay_" + UUID.randomUUID().toString().substring(0, 12);
            refundableByCharge.put(ref, request.amount());
            return GatewayResult.approved(ref);
        });
    }

    @Override
    public synchronized GatewayResult refund(GatewayRefundRequest request) {
        return once(request.idempotencyKey(), request, () -> {
            Money refundable = refundableByCharge.get(request.chargeRef());
            if (refundable == null || request.amount().compareTo(refundable) > 0) {
                return GatewayResult.declined("Refund exceeds what is left of charge " + request.chargeRef());
            }
            refundableByCharge.put(request.chargeRef(), refundable.minus(request.amount()));
            return GatewayResult.approved("rfd_" + UUID.randomUUID().toString().substring(0, 12));
        });
    }

    private GatewayResult once(String key, Object request, Supplier<GatewayResult> call) {
        Seen seen = byKey.get(key);
        if (seen != null) {
            if (!seen.request().equals(request)) {
                throw new IllegalArgumentException("Idempotency key " + key + " was used for a different request");
            }
            return seen.result();
        }
        Scripted next = scripted.poll();
        if (next != null && next.declineReason() == null) {
            throw new GatewayUnavailableException("Payment gateway timed out; nothing was charged");
        }
        GatewayResult result = next != null ? GatewayResult.declined(next.declineReason()) : call.get();
        if (result.approved()) {
            moneyMovements++;
        }
        byKey.put(key, new Seen(request, result));
        return result;
    }
}
