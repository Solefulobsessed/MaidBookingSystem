package com.example.maidbooking.capacity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code utilisationFactor}: share of raw maid minutes that may be sold (default 0.85), because jobs
 * never pack perfectly into calendars and travel eats some time.
 */
@ConfigurationProperties(prefix = "app.capacity")
public record CapacityProperties(Double utilisationFactor) {

    public CapacityProperties {
        utilisationFactor = utilisationFactor == null ? 0.85 : utilisationFactor;
        if (utilisationFactor <= 0 || utilisationFactor > 1) {
            throw new IllegalArgumentException("app.capacity.utilisation-factor must be in (0, 1]: " + utilisationFactor);
        }
    }
}
