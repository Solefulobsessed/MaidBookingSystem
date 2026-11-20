package com.example.maidbooking.common.error;

/** Stable, client-facing error codes from the spec, each with the HTTP status it maps to. */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    NOT_FOUND(404),
    PAYMENT_FAILED(402),
    SLOT_UNAVAILABLE(409),
    MAID_UNAVAILABLE(409),
    NO_MAID_NEARBY(409),
    INVALID_STATE(409),
    HOLD_EXPIRED(409),
    SHIFT_CONFLICT(409),
    REQUEST_IN_PROGRESS(409),
    IDEMPOTENCY_KEY_REUSED(422),
    GATEWAY_ERROR(502);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
