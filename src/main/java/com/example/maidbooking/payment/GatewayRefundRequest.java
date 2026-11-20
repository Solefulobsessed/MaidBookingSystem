package com.example.maidbooking.payment;

import java.util.Objects;

import com.example.maidbooking.common.Money;

/** @param chargeRef the gateway reference of the charge being refunded */
public record GatewayRefundRequest(String idempotencyKey, String chargeRef, Money amount) {

    public GatewayRefundRequest {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(chargeRef, "chargeRef");
        Objects.requireNonNull(amount, "amount");
    }
}
