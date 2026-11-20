package com.example.maidbooking.payment;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ValidationException;

/** Finds the processor for a payment method; built from every {@link PaymentProcessor} bean. */
@Component
public class PaymentProcessorRegistry {

    private final Map<PaymentMethodType, PaymentProcessor> processors;

    public PaymentProcessorRegistry(List<PaymentProcessor> processors) {
        // toMap fails with IllegalStateException if two processors claim the same method
        this.processors = Map.copyOf(processors.stream()
                .collect(Collectors.toMap(PaymentProcessor::method, Function.identity())));
    }

    public PaymentProcessor processorFor(PaymentMethodType method) {
        PaymentProcessor processor = processors.get(method);
        if (processor == null) {
            throw new ValidationException("Payment method " + method + " is not supported");
        }
        return processor;
    }
}
