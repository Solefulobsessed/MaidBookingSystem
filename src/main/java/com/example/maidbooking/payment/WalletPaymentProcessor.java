package com.example.maidbooking.payment;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.payment.PaymentDetails.WalletDetails;

/** Wallet: a supported provider and the Indian mobile number the wallet is registered to. */
@Component
public class WalletPaymentProcessor extends GatewayPaymentProcessor<WalletDetails> {

    static final Set<String> PROVIDERS = Set.of("PAYTM", "PHONEPE", "AMAZONPAY", "MOBIKWIK");

    public WalletPaymentProcessor(PaymentGateway gateway) {
        super(PaymentMethodType.WALLET, gateway, WalletDetails.class);
    }

    @Override
    void check(WalletDetails wallet) {
        if (wallet.provider() == null || !PROVIDERS.contains(wallet.provider().toUpperCase())
                || wallet.phone() == null || !wallet.phone().matches("(\\+91)?[6-9]\\d{9}")) {
            throw new ValidationException("Wallet needs a provider from " + PROVIDERS + " and an Indian mobile number");
        }
    }

    @Override
    String instrument(WalletDetails wallet) {
        return wallet.provider().toUpperCase() + ":" + wallet.phone().substring(wallet.phone().length() - 4);
    }
}
