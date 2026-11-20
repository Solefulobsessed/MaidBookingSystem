package com.example.maidbooking.pricing;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/** The rate card: an hourly rate per service and a multiplier per tier, from {@code app.pricing}. */
@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(Map<ServiceType, BigDecimal> hourlyRates, Map<Tier, BigDecimal> tierMultipliers) {

    public PricingProperties {
        hourlyRates = requireComplete(hourlyRates, ServiceType.class, "hourly-rates");
        tierMultipliers = requireComplete(tierMultipliers, Tier.class, "tier-multipliers");
    }

    private static <K extends Enum<K>> Map<K, BigDecimal> requireComplete(Map<K, BigDecimal> values, Class<K> type,
                                                                         String name) {
        for (K key : type.getEnumConstants()) {
            BigDecimal value = values == null ? null : values.get(key);
            if (value == null || value.signum() <= 0) {
                throw new IllegalArgumentException("app.pricing." + name + " needs a positive value for " + key);
            }
        }
        return Map.copyOf(new EnumMap<>(values));
    }
}
