package com.example.maidbooking.allocation.scoring;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Weight per scoring rule name, from {@code app.allocation.weights}. A rule with no weight counts 1.0. */
@ConfigurationProperties(prefix = "app.allocation")
public record ScoringProperties(Map<String, Double> weights) {

    public ScoringProperties {
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        weights.forEach((rule, weight) -> {
            if (weight < 0) {
                throw new IllegalArgumentException("Weight for " + rule + " cannot be negative");
            }
        });
    }

    public double weightOf(String rule) {
        return weights.getOrDefault(rule, 1.0);
    }
}
