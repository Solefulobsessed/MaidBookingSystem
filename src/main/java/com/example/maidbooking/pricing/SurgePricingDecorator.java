package com.example.maidbooking.pricing;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.maidbooking.capacity.CapacityService;
import com.example.maidbooking.common.Money;

/**
 * Wraps the rate card: +20% when under 20% of the slot's capacity is left, + a fixed premium for instant jobs.
 * It is the primary {@link PricingStrategy}, so slot quotes, maid search, instant checks and the booking itself
 * all price the same way, and the job's price is fixed before its own reservation is made.
 */
@Primary
@Component
public class SurgePricingDecorator implements PricingStrategy {

    private final PricingStrategy base;
    private final CapacityService capacity;
    private final SurgeProperties surge;

    public SurgePricingDecorator(TierPricing base, CapacityService capacity, SurgeProperties surge) {
        this.base = base;
        this.capacity = capacity;
        this.surge = surge;
    }

    @Override
    public Money price(PriceQuery query) {
        Money price = base.price(query);
        if (capacity.freeShare(query.zoneId(), query.service(), query.slot()) < surge.freeShareBelow()) {
            price = price.plus(price.percent(surge.upliftPercent()));
        }
        return query.instant() ? price.plus(new Money(surge.instantPremium())) : price;
    }
}
