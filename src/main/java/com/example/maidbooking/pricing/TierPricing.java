package com.example.maidbooking.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.Money;

/** Price = hourly rate of the service x hours x tier multiplier. */
@Component
public class TierPricing implements PricingStrategy {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final PricingProperties rateCard;

    public TierPricing(PricingProperties rateCard) {
        this.rateCard = rateCard;
    }

    @Override
    public Money price(PriceQuery query) {
        BigDecimal hours = BigDecimal.valueOf(query.slot().duration().toMinutes())
                .divide(MINUTES_PER_HOUR, 6, RoundingMode.HALF_UP);
        return new Money(rateCard.hourlyRates().get(query.service())
                .multiply(hours)
                .multiply(rateCard.tierMultipliers().get(query.tier())));
    }
}
