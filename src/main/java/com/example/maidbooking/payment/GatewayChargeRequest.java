package com.example.maidbooking.payment;

import java.util.Objects;

import com.example.maidbooking.common.Money;

/** @param instrument what is charged, already masked by the processor, e.g. {@code UPI:user@okbank} */
public record GatewayChargeRequest(String idempotencyKey, Money amount, String instrument) {

    public GatewayChargeRequest {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(instrument, "instrument");
    }
}
