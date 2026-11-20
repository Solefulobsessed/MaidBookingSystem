package com.example.maidbooking.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.payment.Payment;
import com.example.maidbooking.payment.PaymentMethodType;
import com.example.maidbooking.supply.ServiceType;

class CancellationPolicyTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private final TimeBasedCancellationPolicy timeBased =
            new TimeBasedCancellationPolicy(new CancellationProperties(null, null, null));

    private static Job job() {
        return new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0)),
                Money.of(249), "cr-1", "4821", null);
    }

    private static Payment paid() {
        Payment payment = new Payment("p-1", "j-1", Money.of(249), PaymentMethodType.UPI, "k-1");
        payment.succeed("pay_1");
        return payment;
    }

    @ParameterizedTest(name = "customer cancels at {0} → {1}% = {2}")
    @CsvSource({
            "04:59, 100, 249.00",
            "05:00, 50, 124.50",
            "07:30, 50, 124.50",
            "08:00, 50, 124.50",
            "08:01, 0, 0.00",
            "08:59, 0, 0.00"})
    void customerRefundDependsOnHowLongBeforeTheStart(String time, int percent, String amount) {
        RefundDecision decision = timeBased.evaluate(job(), paid(), CancelledBy.CUSTOMER,
                MON.atTime(LocalTime.parse(time)));

        assertThat(decision.percent()).isEqualTo(percent);
        assertThat(decision.amount()).isEqualTo(Money.of(amount));
    }

    @Test
    void platformCausedIsAlwaysAFullRefund() {
        RefundDecision decision = timeBased.evaluate(job(), paid(), CancelledBy.PLATFORM, MON.atTime(8, 59));

        assertThat(decision.percent()).isEqualTo(100);
        assertThat(decision.amount()).isEqualTo(Money.of(249));
    }

    @Test
    void anUnpaidJobRefundsNothing() {
        RefundDecision decision = timeBased.evaluate(job(), null, CancelledBy.CUSTOMER, MON.minusDays(1).atTime(9, 0));

        assertThat(decision.amount()).isEqualTo(Money.ZERO);
        assertThat(decision.reason()).isEqualTo("Nothing was paid");
    }

    @Test
    void neverRefundsMoreThanIsLeft() {
        Payment payment = paid();
        payment.recordRefund(Money.of(200));

        assertThat(timeBased.evaluate(job(), payment, CancelledBy.PLATFORM, MON.atTime(6, 0)).amount())
                .isEqualTo(Money.of(49));
    }

    @Test
    void cannotCancelOnceTheMaidIsOnHerWay() {
        Job job = job();
        job.confirm("p-1");
        job.assign("m-01", "as-1");
        job.enRoute();

        assertThatThrownBy(() -> timeBased.evaluate(job, paid(), CancelledBy.CUSTOMER, MON.atTime(8, 0)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThatThrownBy(() -> new NoRefundPolicy().evaluate(job, paid(), CancelledBy.PLATFORM, MON.atTime(8, 0)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void windowsComeFromConfiguration() {
        TimeBasedCancellationPolicy strict = new TimeBasedCancellationPolicy(new CancellationProperties(24, 12, 25));

        assertThat(strict.evaluate(job(), paid(), CancelledBy.CUSTOMER, MON.atTime(5, 0)).amount())
                .isEqualTo(Money.ZERO);
        assertThat(strict.evaluate(job(), paid(), CancelledBy.CUSTOMER, MON.minusDays(1).atTime(20, 0)).amount())
                .isEqualTo(Money.of("62.25"));
        assertThatThrownBy(() -> new CancellationProperties(1, 4, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRefundPolicyRefundsOnlyPlatformCausedCancellations() {
        NoRefundPolicy noRefund = new NoRefundPolicy();

        assertThat(noRefund.evaluate(job(), paid(), CancelledBy.CUSTOMER, MON.minusDays(3).atTime(9, 0)).amount())
                .isEqualTo(Money.ZERO);
        assertThat(noRefund.evaluate(job(), paid(), CancelledBy.PLATFORM, MON.atTime(8, 0)).amount())
                .isEqualTo(Money.of(249));
    }

    @Configuration
    @EnableConfigurationProperties(CancellationProperties.class)
    @Import({TimeBasedCancellationPolicy.class, NoRefundPolicy.class})
    static class Policies {
    }

    @Test
    void thePolicyIsPickedByProperty() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Policies.class);

        runner.run(context -> assertThat(context.getBean(CancellationPolicy.class))
                .isInstanceOf(TimeBasedCancellationPolicy.class));
        runner.withPropertyValues("app.cancellation.policy=no-refund")
                .run(context -> assertThat(context.getBean(CancellationPolicy.class)).isInstanceOf(NoRefundPolicy.class));
    }
}
