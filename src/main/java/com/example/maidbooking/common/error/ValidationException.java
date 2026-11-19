package com.example.maidbooking.common.error;

import java.util.Map;

public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message, Map.of());
    }
}
