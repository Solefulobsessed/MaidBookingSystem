package com.example.maidbooking.payment;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;

/** UPI: a virtual payment address such as {@code user@okbank}. */
@Component
public class UpiPaymentProcessor extends GatewayPaymentProcessor<UpiDetails> {

    public UpiPaymentProcessor(PaymentGateway gateway) {
        super(PaymentMethodType.UPI, gateway, UpiDetails.class);
    }

    @Override
    void check(UpiDetails upi) {
        if (upi.vpa() == null || !upi.vpa().matches("[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}")) {
            throw new ValidationException("UPI ID must look like name@bank, was " + upi.vpa());
        }
    }

    @Override
    String instrument(UpiDetails upi) {
        return upi.vpa();
    }
}
