package com.example.maidbooking.pricing;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

/** The default rate card from application.yml, for unit tests. */
public final class TestRateCard {

    private TestRateCard() {
    }

    public static PricingProperties standard() {
        Map<ServiceType, BigDecimal> rates = new EnumMap<>(ServiceType.class);
        rates.put(ServiceType.CLEANING, new BigDecimal("249"));
        rates.put(ServiceType.COOKING, new BigDecimal("299"));
        rates.put(ServiceType.DISHWASHING, new BigDecimal("199"));
        rates.put(ServiceType.LAUNDRY, new BigDecimal("199"));
        rates.put(ServiceType.BABYSITTING, new BigDecimal("349"));
        return new PricingProperties(rates, Map.of(Tier.STANDARD, BigDecimal.ONE, Tier.PREMIUM, new BigDecimal("1.3")));
    }
}
