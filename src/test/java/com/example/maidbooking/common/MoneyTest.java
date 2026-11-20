package com.example.maidbooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ValidationException;

class MoneyTest {

    @Test
    void alwaysHoldsTwoDecimalsWithHalfUpRounding() {
        assertThat(Money.of(249).amount()).isEqualByComparingTo("249.00");
        assertThat(Money.of("124.505").amount()).isEqualTo(new BigDecimal("124.51"));
        assertThat(Money.of("124.504").amount()).isEqualTo(new BigDecimal("124.50"));
    }

    @Test
    void equalAmountsAreEqualWhateverTheInputScale() {
        assertThat(Money.of("249")).isEqualTo(Money.of("249.000"));
    }

    @Test
    void arithmetic() {
        Money rate = Money.of(249);

        assertThat(rate.plus(Money.of("0.50"))).isEqualTo(Money.of("249.50"));
        assertThat(rate.minus(Money.of(49))).isEqualTo(Money.of(200));
        assertThat(rate.times(new BigDecimal("1.5"))).isEqualTo(Money.of("373.50"));
    }

    @Test
    void percentForRefunds() {
        assertThat(Money.of(249).percent(50)).isEqualTo(Money.of("124.50"));
        assertThat(Money.of("249.99").percent(50)).isEqualTo(Money.of("125.00"));
        assertThat(Money.of(249).percent(0)).isEqualTo(Money.ZERO);
        assertThat(Money.of(249).percent(100)).isEqualTo(Money.of(249));
    }

    @Test
    void neverNegative() {
        assertThatThrownBy(() -> Money.of("-1")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.of(10).minus(Money.of(11))).isInstanceOf(ValidationException.class);
    }

    @Test
    void comparesAndPrints() {
        assertThat(Money.of(100)).isLessThan(Money.of(200));
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("249.5")).hasToString("INR 249.50");
    }
}
