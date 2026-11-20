package com.example.maidbooking.payment;

import com.example.maidbooking.common.Money;

/** One payment method. Add a method by adding a {@link PaymentMethodType} value and one processor bean. */
public interface PaymentProcessor {

    PaymentMethodType method();

    /** @throws com.example.maidbooking.common.error.ValidationException if the details are unusable */
    void validate(PaymentDetails details);

    GatewayResult charge(Money amount, PaymentDetails details, String idempotencyKey);

    GatewayResult refund(Payment payment, Money amount);
}
