package com.example.maidbooking.common.error;

import java.util.Map;

/** A request that is valid but cannot be served in the current state (slot full, hold expired, ...). */
public class ConflictException extends DomainException {

    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
