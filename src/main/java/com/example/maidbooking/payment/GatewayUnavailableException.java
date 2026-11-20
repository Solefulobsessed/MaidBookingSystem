package com.example.maidbooking.payment;

import java.util.Map;

import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;

/** The payment provider could not be reached (502). Nothing was charged or refunded, so a retry is safe. */
public class GatewayUnavailableException extends DomainException {

    public GatewayUnavailableException(String message) {
        super(ErrorCode.GATEWAY_ERROR, message, Map.of());
    }
}
