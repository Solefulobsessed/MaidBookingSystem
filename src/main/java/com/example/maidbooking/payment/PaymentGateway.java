package com.example.maidbooking.payment;

/**
 * The external payment provider. Both calls are idempotent on the request's key: repeating a key returns the
 * first result without moving money again.
 *
 * @throws GatewayUnavailableException if the provider can't be reached; nothing was charged or refunded
 */
public interface PaymentGateway {

    GatewayResult charge(GatewayChargeRequest request);

    GatewayResult refund(GatewayRefundRequest request);
}
