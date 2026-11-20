package com.example.maidbooking.discovery.filter;

import java.util.Map;

import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;

public class InvalidFilterException extends DomainException {

    public InvalidFilterException(String key, String value, String reason) {
        super(ErrorCode.VALIDATION_FAILED, "Invalid filter " + key + "=" + value + ": " + reason,
                Map.of("filter", key, "value", String.valueOf(value)));
    }
}
