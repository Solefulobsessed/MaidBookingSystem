package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.InvalidStateTransitionException;

class PaymentTest {

    private static Payment payment() {
        return new Payment("p-1", "j-1", Money.of(249), PaymentMethodType.UPI, "k-1");
    }

    private static Payment succeeded() {
        Payment payment = payment();
        payment.succeed("pay_1");
        return payment;
    }

    @Test
    void startsInitiatedThenSucceedsOrFails() {
        Payment ok = payment();
        assertThat(ok.status()).isEqualTo(PaymentStatus.INITIATED);
        ok.succeed("pay_1");
        assertThat(ok.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(ok.gatewayRef()).isEqualTo("pay_1");

        Payment declined = payment();
        declined.fail();
        assertThat(declined.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.gatewayRef()).isNull();
    }

    @Test
    void anOutcomeIsFinal() {
        Payment ok = succeeded();
        assertThatThrownBy(ok::fail).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> ok.succeed("pay_2")).isInstanceOf(InvalidStateTransitionException.class);

        Payment declined = payment();
        declined.fail();
        assertThatThrownBy(() -> declined.succeed("pay_2")).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void partialRefundsAddUpToAFullRefund() {
        Payment payment = succeeded();

        payment.recordRefund(Money.of("124.50"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundedAmount()).isEqualTo(Money.of("124.50"));
        assertThat(payment.refundable()).isEqualTo(Money.of("124.50"));

        payment.recordRefund(Money.of("124.50"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundable()).isEqualTo(Money.ZERO);
        assertThatThrownBy(() -> payment.recordRefund(Money.of(1))).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotRefundMoreThanWasPaidOrNothingAtAll() {
        Payment payment = succeeded();

        assertThatThrownBy(() -> payment.recordRefund(Money.of(250))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.recordRefund(Money.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void onlyASucceededPaymentCanBeRefunded() {
        Payment declined = payment();
        declined.fail();

        assertThatThrownBy(() -> payment().recordRefund(Money.of(10))).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> declined.recordRefund(Money.of(10))).isInstanceOf(InvalidStateTransitionException.class);
    }
}
