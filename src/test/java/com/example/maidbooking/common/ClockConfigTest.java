package com.example.maidbooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockUsesConfiguredTimeZone() {
        Clock clock = new ClockConfig().clock("Asia/Kolkata");

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
