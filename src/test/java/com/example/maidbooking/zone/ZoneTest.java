package com.example.maidbooking.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ValidationException;

class ZoneTest {

    @Test
    void localitiesAreNormalised() {
        Zone zone = new Zone("kor-5", "Koramangala 5th Block", Set.of("  Koramangala   5th Block "), Set.of());

        assertThat(zone.localities()).containsExactly("koramangala 5th block");
        assertThat(zone.covers("KORAMANGALA 5th block")).isTrue();
        assertThat(zone.covers("Indiranagar")).isFalse();
    }

    @Test
    void neighbourRelationWorksFromEitherSide() {
        Zone a = new Zone("a", "A", Set.of("a"), Set.of("b"));
        Zone b = new Zone("b", "B", Set.of("b"), Set.of());
        Zone c = new Zone("c", "C", Set.of("c"), Set.of());

        assertThat(a.isNeighbourOf(b)).isTrue();
        assertThat(b.isNeighbourOf(a)).isTrue();
        assertThat(a.isNeighbourOf(c)).isFalse();
    }

    @Test
    void rejectsZoneWithoutLocalitiesOrSelfNeighbour() {
        assertThatThrownBy(() -> new Zone("a", "A", Set.of(), Set.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Zone("a", "A", Set.of("a"), Set.of("a"))).isInstanceOf(ValidationException.class);
    }

    @Test
    void missingNeighboursMeansNone() {
        assertThat(new Zone("a", "A", Set.of("a"), null).neighbourZoneIds()).isEmpty();
    }
}
