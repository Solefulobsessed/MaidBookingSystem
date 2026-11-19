package com.example.maidbooking.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void codesMapToTheStatusesInTheSpec() {
        assertThat(ErrorCode.VALIDATION_FAILED.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.PAYMENT_FAILED.httpStatus()).isEqualTo(402);
        assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.HOLD_EXPIRED.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.IDEMPOTENCY_KEY_REUSED.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.GATEWAY_ERROR.httpStatus()).isEqualTo(502);
    }

    @Test
    void detailsAreImmutableCopies() {
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("k", "v");
        ConflictException ex = new ConflictException(ErrorCode.SHIFT_CONFLICT, "overlap", details);
        details.put("k2", "v2");

        assertThat(ex.details()).containsOnlyKeys("k");
    }
}
