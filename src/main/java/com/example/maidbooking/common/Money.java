package com.example.maidbooking.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonValue;

import com.example.maidbooking.common.error.ValidationException;

/** A non-negative INR amount, always held at 2 decimal places with HALF_UP rounding. In JSON it is a plain number. */
public record Money(@JsonValue BigDecimal amount) implements Comparable<Money> {

    public static final String CURRENCY = "INR";
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new ValidationException("Money cannot be negative: " + amount);
        }
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long rupees) {
        return new Money(BigDecimal.valueOf(rupees));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    /** e.g. {@code percent(50)} for a 50% refund. */
    public Money percent(int percent) {
        return times(BigDecimal.valueOf(percent).movePointLeft(2));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return CURRENCY + " " + amount.toPlainString();
    }
}
