package com.example.maidbooking.payment;

/** Approved with a gateway reference, or declined with a reason. An outage is an exception, not a result. */
public record GatewayResult(boolean approved, String gatewayRef, String declineReason) {

    public static GatewayResult approved(String gatewayRef) {
        return new GatewayResult(true, gatewayRef, null);
    }

    public static GatewayResult declined(String reason) {
        return new GatewayResult(false, null, reason);
    }
}
