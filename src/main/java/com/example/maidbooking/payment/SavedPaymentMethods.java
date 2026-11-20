package com.example.maidbooking.payment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** The payment method a subscription is charged with before each visit, kept by subscription id. */
@Component
public class SavedPaymentMethods {

    private final PaymentProcessorRegistry processors;
    private final Map<String, PaymentDetails> bySubscription = new ConcurrentHashMap<>();

    public SavedPaymentMethods(PaymentProcessorRegistry processors) {
        this.processors = processors;
    }

    /** Checks the details up front, so a booking is never created with a method that can't be charged. */
    public void check(PaymentDetails details) {
        processors.processorFor(details.method()).validate(details);
    }

    public void save(String subscriptionId, PaymentDetails details) {
        check(details);
        bySubscription.put(subscriptionId, details);
    }

    public Optional<PaymentDetails> find(String subscriptionId) {
        return Optional.ofNullable(bySubscription.get(subscriptionId));
    }
}
