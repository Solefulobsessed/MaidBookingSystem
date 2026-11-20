package com.example.maidbooking.pricing;

import com.example.maidbooking.common.Money;

/**
 * Prices a job. Search quotes and the amount charged use the same strategy, so they always agree.
 * Prices are set by the platform, not by individual maids.
 */
public interface PricingStrategy {

    Money price(PriceQuery query);
}
