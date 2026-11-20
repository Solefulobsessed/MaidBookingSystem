package com.example.maidbooking.cancellation;

import com.example.maidbooking.common.Money;

/** How much of the payment goes back, and why. {@code amount} is zero when nothing was paid. */
public record RefundDecision(int percent, Money amount, String reason) {
}
