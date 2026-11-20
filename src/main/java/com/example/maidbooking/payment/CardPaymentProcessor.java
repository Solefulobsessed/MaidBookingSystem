package com.example.maidbooking.payment;

import java.time.Clock;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.payment.PaymentDetails.CardDetails;

/** Card: 12–19 digits passing the Luhn check, and not expired. */
@Component
public class CardPaymentProcessor extends GatewayPaymentProcessor<CardDetails> {

    private final Clock clock;

    public CardPaymentProcessor(PaymentGateway gateway, Clock clock) {
        super(PaymentMethodType.CARD, gateway, CardDetails.class);
        this.clock = clock;
    }

    @Override
    void check(CardDetails card) {
        if (card.number() == null || !digits(card).matches("\\d{12,19}") || !passesLuhn(digits(card))) {
            throw new ValidationException("Card number is not valid");
        }
        if (card.expiry() == null || card.expiry().isBefore(YearMonth.now(clock))) {
            throw new ValidationException("Card has expired");
        }
    }

    @Override
    String instrument(CardDetails card) {
        return "**** " + digits(card).substring(digits(card).length() - 4);
    }

    private static String digits(CardDetails card) {
        return card.number().replace(" ", "");
    }

    private static boolean passesLuhn(String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = (number.charAt(number.length() - 1 - i) - '0') * (i % 2 == 1 ? 2 : 1);
            sum += digit > 9 ? digit - 9 : digit;
        }
        return sum % 10 == 0;
    }
}
