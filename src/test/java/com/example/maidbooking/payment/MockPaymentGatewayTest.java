package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.ErrorCode;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    private static GatewayChargeRequest charge(String key, long rupees, String instrument) {
        return new GatewayChargeRequest(key, Money.of(rupees), instrument);
    }

    @Test
    void approvesAChargeWithAReference() {
        GatewayResult result = gateway.charge(charge("k-1", 249, "UPI:user@okbank"));

        assertThat(result.approved()).isTrue();
        assertThat(result.gatewayRef()).startsWith("pay_");
        assertThat(result.declineReason()).isNull();
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }

    @Test
    void declinesAnInstrumentContainingDecline() {
        GatewayResult result = gateway.charge(charge("k-1", 249, "UPI:decline@okbank"));

        assertThat(result.approved()).isFalse();
        assertThat(result.gatewayRef()).isNull();
        assertThat(result.declineReason()).isEqualTo("Declined by issuer");
        assertThat(gateway.moneyMovements()).isZero();
    }

    @Test
    void aReplayedKeyReturnsTheFirstResultWithoutChargingAgain() {
        GatewayResult first = gateway.charge(charge("k-1", 249, "CARD:4242"));

        assertThat(gateway.charge(charge("k-1", 249, "CARD:4242"))).isEqualTo(first);
        assertThat(gateway.moneyMovements()).isEqualTo(1);
        assertThat(gateway.charge(charge("k-2", 249, "CARD:4242")).gatewayRef()).isNotEqualTo(first.gatewayRef());
    }

    @Test
    void aReplayedDeclineStaysDeclined() {
        gateway.declineNext("Insufficient funds");

        GatewayResult first = gateway.charge(charge("k-1", 249, "CARD:4242"));

        assertThat(first.declineReason()).isEqualTo("Insufficient funds");
        assertThat(gateway.charge(charge("k-1", 249, "CARD:4242"))).isEqualTo(first);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        gateway.charge(charge("k-1", 249, "CARD:4242"));

        assertThatThrownBy(() -> gateway.charge(charge("k-1", 499, "CARD:4242")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOutageThrowsAndTheSameKeyCanBeRetried() {
        gateway.failNext();

        assertThatThrownBy(() -> gateway.charge(charge("k-1", 249, "CARD:4242")))
                .isInstanceOfSatisfying(GatewayUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.GATEWAY_ERROR));
        assertThat(gateway.charge(charge("k-1", 249, "CARD:4242")).approved()).isTrue();
        assertThat(gateway.moneyMovements()).isEqualTo(1);
    }

    @Test
    void refundsUpToWhatWasCharged() {
        String ref = gateway.charge(charge("k-1", 249, "CARD:4242")).gatewayRef();

        GatewayResult half = gateway.refund(new GatewayRefundRequest("r-1", ref, Money.of("124.50")));
        GatewayResult tooMuch = gateway.refund(new GatewayRefundRequest("r-2", ref, Money.of(125)));
        GatewayResult rest = gateway.refund(new GatewayRefundRequest("r-3", ref, Money.of("124.50")));

        assertThat(half.approved()).isTrue();
        assertThat(half.gatewayRef()).startsWith("rfd_");
        assertThat(tooMuch.approved()).isFalse();
        assertThat(rest.approved()).isTrue();
        assertThat(gateway.refund(new GatewayRefundRequest("r-1", ref, Money.of("124.50")))).as("replay")
                .isEqualTo(half);
        assertThat(gateway.moneyMovements()).isEqualTo(3);
    }

    @Test
    void refundOfAnUnknownChargeIsDeclined() {
        assertThat(gateway.refund(new GatewayRefundRequest("r-1", "pay_nope", Money.of(10))).approved()).isFalse();
    }
}
