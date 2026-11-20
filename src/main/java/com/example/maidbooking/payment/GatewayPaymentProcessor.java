package com.example.maidbooking.payment;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.ValidationException;

/** Shared gateway plumbing; each method supplies its details type, its checks and a masked instrument label. */
abstract class GatewayPaymentProcessor<D extends PaymentDetails> implements PaymentProcessor {

    private final PaymentMethodType method;
    private final PaymentGateway gateway;
    private final Class<D> detailsType;

    GatewayPaymentProcessor(PaymentMethodType method, PaymentGateway gateway, Class<D> detailsType) {
        this.method = method;
        this.gateway = gateway;
        this.detailsType = detailsType;
    }

    @Override
    public PaymentMethodType method() {
        return method;
    }

    abstract void check(D details);

    /** What the gateway sees, e.g. {@code CARD:**** 4242}; never the full card number. */
    abstract String instrument(D details);

    @Override
    public void validate(PaymentDetails details) {
        if (!detailsType.isInstance(details)) {
            throw new ValidationException(method() + " payments need " + method() + " details");
        }
        check(detailsType.cast(details));
    }

    @Override
    public GatewayResult charge(Money amount, PaymentDetails details, String idempotencyKey) {
        validate(details);
        return gateway.charge(new GatewayChargeRequest(idempotencyKey, amount,
                method() + ":" + instrument(detailsType.cast(details))));
    }

    /** The key is derived from what has been refunded so far, so retrying the same refund is harmless. */
    @Override
    public GatewayResult refund(Payment payment, Money amount) {
        return gateway.refund(new GatewayRefundRequest(payment.id() + ":refund:" + payment.refundedAmount()
                + ":" + amount, payment.gatewayRef(), amount));
    }
}
