package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.RecurrenceRule;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class SubscriptionTest {

    private static Subscription subscription() {
        LocalDate mon = LocalDate.of(2026, 11, 23);
        return new Subscription("sub-1", "c-1", "kor-5", ServiceType.CLEANING,
                new RecurrenceRule(Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), Duration.ofHours(1), mon,
                        mon.plusMonths(1)), Tier.STANDARD, null, mon.plusDays(13));
    }

    @Test
    void startsActiveAndCanPauseResumeAndCancel() {
        Subscription subscription = subscription();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);

        subscription.pause();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PAUSED);
        subscription.resume();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        subscription.pause();
        subscription.cancel();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void rejectsIllegalMoves() {
        Subscription subscription = subscription();
        assertThatThrownBy(subscription::resume).isInstanceOf(InvalidStateTransitionException.class);

        subscription.cancel();
        assertThatThrownBy(subscription::cancel).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(subscription::pause).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(subscription::resume).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void extendsOnlyForwardAndOnlyWhileActive() {
        Subscription subscription = subscription();
        LocalDate until = subscription.generatedUntil();

        subscription.extendTo(until.plusDays(1));
        assertThat(subscription.generatedUntil()).isEqualTo(until.plusDays(1));
        assertThatThrownBy(() -> subscription.extendTo(until)).isInstanceOf(IllegalArgumentException.class);

        subscription.pause();
        assertThatThrownBy(() -> subscription.extendTo(until.plusDays(2)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void remembersSkippedDatesInOrder() {
        Subscription subscription = subscription();
        LocalDate day = LocalDate.of(2026, 12, 7);

        subscription.skipNoCapacity(day.plusDays(7));
        subscription.skipNoCapacity(day);

        assertThat(subscription.skippedNoCapacity()).containsExactly(day, day.plusDays(7));
    }
}
