package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;

class JobGuardTest {

    private final JobGuard guard = new JobGuard();

    @Test
    void aSecondActionOnTheSameJobIsRefusedWhileTheFirstRuns() {
        String result = guard.exclusive("j-1", () -> {
            assertThatThrownBy(() -> guard.exclusive("j-1", () -> "cancel"))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
            return guard.exclusive("j-2", () -> "other job is fine") + " / pay";
        });

        assertThat(result).isEqualTo("other job is fine / pay");
    }

    @Test
    void theJobIsFreedEvenWhenTheActionFails() {
        assertThatThrownBy(() -> guard.exclusive("j-1", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.exclusive("j-1", () -> "ok")).isEqualTo("ok");
    }
}
