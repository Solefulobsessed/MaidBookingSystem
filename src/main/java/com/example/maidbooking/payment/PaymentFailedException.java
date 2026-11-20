package com.example.maidbooking.payment;

import java.util.Map;

import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;

/** The gateway declined the charge (402). The job stays payable until its hold expires. */
public class PaymentFailedException extends DomainException {

    public PaymentFailedException(String paymentId, String reason) {
        super(ErrorCode.PAYMENT_FAILED, "Payment declined: " + reason, Map.of("paymentId", paymentId));
    }
}
