package com.example.maidbooking.payment;

import java.time.YearMonth;

/** How the customer pays. Each method's processor checks its own details. */
public sealed interface PaymentDetails {

    PaymentMethodType method();

    /** The CVV never reaches this backend: the app hands it straight to the provider. */
    record CardDetails(String number, YearMonth expiry) implements PaymentDetails {
        public PaymentMethodType method() { return PaymentMethodType.CARD; }
    }

    record UpiDetails(String vpa) implements PaymentDetails {
        public PaymentMethodType method() { return PaymentMethodType.UPI; }
    }

    record WalletDetails(String provider, String phone) implements PaymentDetails {
        public PaymentMethodType method() { return PaymentMethodType.WALLET; }
    }
}
