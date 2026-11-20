package com.example.maidbooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class NearestIdleDispatchStrategyTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    @Test
    void startTimesRoundUpToTheNextFiveMinutes() {
        assertThat(NearestIdleDispatchStrategy.roundUp(MON.atTime(10, 2, 30))).isEqualTo(MON.atTime(10, 5));
        assertThat(NearestIdleDispatchStrategy.roundUp(MON.atTime(10, 5))).isEqualTo(MON.atTime(10, 5));
        assertThat(NearestIdleDispatchStrategy.roundUp(MON.atTime(10, 5, 1))).isEqualTo(MON.atTime(10, 10));
        assertThat(NearestIdleDispatchStrategy.roundUp(MON.atTime(23, 58))).isEqualTo(MON.plusDays(1).atStartOfDay());
    }
}
