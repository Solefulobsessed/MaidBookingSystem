package com.example.maidbooking.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock tests can set and move forward, in IST. */
public class MutableClock extends Clock {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private Instant now;

    public MutableClock(LocalDateTime start) {
        this.now = start.atZone(IST).toInstant();
    }

    public void advance(Duration by) {
        now = now.plus(by);
    }

    public void set(LocalDateTime time) {
        now = time.atZone(IST).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return IST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
