package com.example.maidbooking.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ValidationException;

class LocalityZoneResolverTest {

    private final ZoneRepository zones = new ZoneRepository();
    private final LocalityZoneResolver resolver = new LocalityZoneResolver(zones);

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("Koramangala", "Koramangala 5th Block"), Set.of("hsr-1")));
        zones.save(new Zone("hsr-1", "HSR Layout", Set.of("HSR Layout"), Set.of()));
    }

    @Test
    void resolvesAnyLocalityOfAZoneIgnoringCaseAndSpaces() {
        assertThat(resolver.resolve(" koramangala ").id()).isEqualTo("kor-5");
        assertThat(resolver.resolve("Koramangala 5th  Block").id()).isEqualTo("kor-5");
        assertThat(resolver.resolve("hsr layout").id()).isEqualTo("hsr-1");
    }

    @Test
    void unknownLocalityIsAValidationError() {
        assertThatThrownBy(() -> resolver.resolve("Whitefield"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Whitefield");
    }

    @Test
    void blankLocalityIsAValidationError() {
        assertThatThrownBy(() -> resolver.resolve("  ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(ValidationException.class);
    }
}
