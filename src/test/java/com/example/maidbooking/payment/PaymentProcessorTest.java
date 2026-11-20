package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.payment.PaymentDetails.CardDetails;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;
import com.example.maidbooking.payment.PaymentDetails.WalletDetails;

class PaymentProcessorTest {

    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final CardPaymentProcessor card = new CardPaymentProcessor(gateway,
            new MutableClock(LocalDateTime.of(2026, 11, 20, 10, 0)));
    private final UpiPaymentProcessor upi = new UpiPaymentProcessor(gateway);
    private final WalletPaymentProcessor wallet = new WalletPaymentProcessor(gateway);

    @Test
    void aValidCardIsChargedWithOnlyItsLastFourDigits() {
        given(gateway.charge(any())).willReturn(GatewayResult.approved("pay_1"));

        GatewayResult result = card.charge(Money.of(249), new CardDetails("4242 4242 4242 4242", NOV_2026), "k-1");

        assertThat(result.approved()).isTrue();
        ArgumentCaptor<GatewayChargeRequest> sent = ArgumentCaptor.forClass(GatewayChargeRequest.class);
        verify(gateway).charge(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new GatewayChargeRequest("k-1", Money.of(249), "CARD:**** 4242"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242 4242 4242 4241", "1234", "4242-4242-4242-4242"})
    void rejectsACardNumberThatIsNotValid(String number) {
        assertThatThrownBy(() -> card.validate(new CardDetails(number, NOV_2026)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Card number");
    }

    @Test
    void rejectsAnExpiredOrMissingCardAndNeverCallsTheGateway() {
        assertThatThrownBy(() -> card.charge(Money.of(249), new CardDetails("4242424242424242", NOV_2026.minusMonths(1)),
                "k-1")).isInstanceOf(ValidationException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> card.validate(new CardDetails(null, NOV_2026))).isInstanceOf(ValidationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void upiNeedsANameAtBank() {
        upi.validate(new UpiDetails("user.name@okbank"));

        for (String bad : new String[] {"userokbank", "u@", "@okbank", "user@ok1bank"}) {
            assertThatThrownBy(() -> upi.validate(new UpiDetails(bad))).as(bad)
                    .isInstanceOf(ValidationException.class).hasMessageContaining("name@bank");
        }
    }

    @Test
    void walletNeedsAKnownProviderAndAnIndianMobile() {
        given(gateway.charge(any())).willReturn(GatewayResult.approved("pay_1"));

        wallet.charge(Money.of(249), new WalletDetails("paytm", "+919876543210"), "k-1");

        verify(gateway).charge(new GatewayChargeRequest("k-1", Money.of(249), "WALLET:PAYTM:3210"));
        assertThatThrownBy(() -> wallet.validate(new WalletDetails("FOOPAY", "9876543210")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> wallet.validate(new WalletDetails("PAYTM", "1234567890")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void detailsOfAnotherMethodAreRejected() {
        assertThatThrownBy(() -> upi.validate(new WalletDetails("PAYTM", "9876543210")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("UPI payments need UPI details");
    }

    @Test
    void refundsTheOriginalChargeWithAKeyThatChangesAfterEachRefund() {
        given(gateway.refund(any())).willReturn(GatewayResult.approved("rfd_1"));
        Payment payment = new Payment("p-1", "j-1", Money.of(249), PaymentMethodType.UPI, "k-1");
        payment.succeed("pay_1");

        upi.refund(payment, Money.of("124.50"));
        payment.recordRefund(Money.of("124.50"));
        upi.refund(payment, Money.of("124.50"));

        ArgumentCaptor<GatewayRefundRequest> sent = ArgumentCaptor.forClass(GatewayRefundRequest.class);
        verify(gateway, times(2)).refund(sent.capture());
        assertThat(sent.getAllValues()).extracting(GatewayRefundRequest::chargeRef).containsOnly("pay_1");
        assertThat(sent.getAllValues()).extracting(GatewayRefundRequest::idempotencyKey).doesNotHaveDuplicates();
    }

    @Test
    void aRetryAfterADeclinedRefundUsesANewKey() {
        given(gateway.refund(any())).willReturn(GatewayResult.declined("no"), GatewayResult.approved("rfd_1"));
        Payment payment = new Payment("p-1", "j-1", Money.of(249), PaymentMethodType.UPI, "k-1");
        payment.succeed("pay_1");

        upi.refund(payment, Money.of(249));
        payment.noteRefundDeclined();
        upi.refund(payment, Money.of(249));

        ArgumentCaptor<GatewayRefundRequest> sent = ArgumentCaptor.forClass(GatewayRefundRequest.class);
        verify(gateway, times(2)).refund(sent.capture());
        assertThat(sent.getAllValues()).extracting(GatewayRefundRequest::idempotencyKey).doesNotHaveDuplicates();
    }

    @Test
    void registryFindsEachMethodAndRejectsUnsupportedOrDuplicateOnes() {
        PaymentProcessorRegistry registry = new PaymentProcessorRegistry(List.of(card, upi, wallet));
        assertThat(registry.processorFor(PaymentMethodType.CARD)).isSameAs(card);
        assertThat(registry.processorFor(PaymentMethodType.WALLET)).isSameAs(wallet);

        assertThatThrownBy(() -> new PaymentProcessorRegistry(List.of(upi)).processorFor(PaymentMethodType.CARD))
                .isInstanceOf(ValidationException.class).hasMessageContaining("CARD");
        assertThatThrownBy(() -> new PaymentProcessorRegistry(List.of(upi, new UpiPaymentProcessor(gateway))))
                .isInstanceOf(IllegalStateException.class);
    }
}
