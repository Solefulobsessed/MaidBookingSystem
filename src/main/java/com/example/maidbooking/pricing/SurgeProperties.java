package com.example.maidbooking.pricing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Surge rules from {@code app.pricing.surge}: when less than {@code freeShareBelow} of a slot's sellable capacity
 * is left, the price goes up by {@code upliftPercent}; instant jobs add a fixed {@code instantPremium}.
 */
@ConfigurationProperties(prefix = "app.pricing.surge")
public record SurgeProperties(Double freeShareBelow, Integer upliftPercent, BigDecimal instantPremium) {

    public SurgeProperties {
        freeShareBelow = freeShareBelow == null ? 0.2 : freeShareBelow;
        upliftPercent = upliftPercent == null ? 20 : upliftPercent;
        instantPremium = instantPremium == null ? new BigDecimal("49") : instantPremium;
        if (freeShareBelow < 0 || freeShareBelow > 1 || upliftPercent < 0 || instantPremium.signum() < 0) {
            throw new IllegalArgumentException("app.pricing.surge needs free-share-below in [0, 1] and "
                    + "non-negative uplift and premium");
        }
    }
}
