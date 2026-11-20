package com.example.maidbooking.api.payment;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.example.maidbooking.payment.PaymentDetails;
import com.example.maidbooking.payment.PaymentDetails.CardDetails;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;
import com.example.maidbooking.payment.PaymentDetails.WalletDetails;
import com.example.maidbooking.payment.PaymentMethodType;

/** {@code { "method": "UPI", "details": { "vpa": "user@okbank" } }}: {@code method} picks the details type. */
public record PaymentBody(
        @NotNull PaymentMethodType method,
        @NotNull
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "method",
                visible = true)
        @JsonSubTypes({@JsonSubTypes.Type(value = CardDetails.class, name = "CARD"),
                @JsonSubTypes.Type(value = UpiDetails.class, name = "UPI"),
                @JsonSubTypes.Type(value = WalletDetails.class, name = "WALLET")})
        PaymentDetails details) {
}
