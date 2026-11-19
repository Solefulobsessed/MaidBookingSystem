package com.example.maidbooking.common.error;

import java.util.Map;

/** Base for every business-rule failure. Carries a code and optional structured details. */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    protected DomainException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
