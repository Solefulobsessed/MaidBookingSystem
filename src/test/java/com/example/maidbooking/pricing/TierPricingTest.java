package com.example.maidbooking.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class TierPricingTest {

    static PricingProperties rateCard() {
        Map<ServiceType, BigDecimal> rates = new EnumMap<>(ServiceType.class);
        rates.put(ServiceType.CLEANING, new BigDecimal("249"));
        rates.put(ServiceType.COOKING, new BigDecimal("299"));
        rates.put(ServiceType.DISHWASHING, new BigDecimal("199"));
        rates.put(ServiceType.LAUNDRY, new BigDecimal("199"));
        rates.put(ServiceType.BABYSITTING, new BigDecimal("349"));
        return new PricingProperties(rates, Map.of(Tier.STANDARD, BigDecimal.ONE, Tier.PREMIUM, new BigDecimal("1.3")));
    }

    private final TierPricing pricing = new TierPricing(rateCard());

    private static PriceQuery query(ServiceType service, Tier tier, int minutes) {
        TimeSlot slot = TimeSlot.of(LocalDate.of(2026, 11, 23).atTime(9, 0), Duration.ofMinutes(minutes));
        return new PriceQuery(service, tier, slot, "kor-5", false);
    }

    @ParameterizedTest(name = "{0} {1} {2} min = {3}")
    @CsvSource({
            "CLEANING,    STANDARD,  60, 249.00",
            "CLEANING,    STANDARD,  90, 373.50",
            "CLEANING,    STANDARD,  50, 207.50",
            "COOKING,     PREMIUM,  120, 777.40",
            "DISHWASHING, STANDARD,  45, 149.25",
            "BABYSITTING, PREMIUM,   30, 226.85"
    })
    void rateTimesHoursTimesTier(ServiceType service, Tier tier, int minutes, String expected) {
        assertThat(pricing.price(query(service, tier, minutes))).isEqualTo(Money.of(expected));
    }

    @Test
    void rateCardMustCoverEveryServiceAndTierWithPositiveValues() {
        Map<ServiceType, BigDecimal> missingLaundry = new EnumMap<>(rateCard().hourlyRates());
        missingLaundry.remove(ServiceType.LAUNDRY);
        Map<ServiceType, BigDecimal> zeroCleaning = new EnumMap<>(rateCard().hourlyRates());
        zeroCleaning.put(ServiceType.CLEANING, BigDecimal.ZERO);

        assertThatThrownBy(() -> new PricingProperties(missingLaundry, rateCard().tierMultipliers()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LAUNDRY");
        assertThatThrownBy(() -> new PricingProperties(zeroCleaning, rateCard().tierMultipliers()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CLEANING");
        assertThatThrownBy(() -> new PricingProperties(rateCard().hourlyRates(), Map.of(Tier.STANDARD, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PREMIUM");
    }
}
