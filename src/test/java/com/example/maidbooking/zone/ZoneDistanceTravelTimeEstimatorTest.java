package com.example.maidbooking.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ZoneDistanceTravelTimeEstimatorTest {

    private final ZoneDistanceTravelTimeEstimator estimator = new ZoneDistanceTravelTimeEstimator(15, 30);
    private final Zone kor = new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of("hsr-1"));
    private final Zone hsr = new Zone("hsr-1", "HSR Layout", Set.of("hsr layout"), Set.of());
    private final Zone whitefield = new Zone("wf-1", "Whitefield", Set.of("whitefield"), Set.of());

    @Test
    void sameZoneIsFifteenMinutes() {
        assertThat(estimator.estimate(kor, kor)).contains(Duration.ofMinutes(15));
    }

    @Test
    void neighbourIsThirtyMinutesInBothDirections() {
        assertThat(estimator.estimate(kor, hsr)).contains(Duration.ofMinutes(30));
        assertThat(estimator.estimate(hsr, kor)).contains(Duration.ofMinutes(30));
    }

    @Test
    void nonNeighbourIsOutOfReach() {
        assertThat(estimator.estimate(kor, whitefield)).isEmpty();
    }
}
