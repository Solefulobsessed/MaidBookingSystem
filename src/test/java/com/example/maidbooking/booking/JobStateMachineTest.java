package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;

class JobStateMachineTest {

    static Job newJob() {
        TimeSlot slot = TimeSlot.of(LocalDate.of(2026, 11, 23).atTime(9, 0), Duration.ofHours(1));
        return new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, slot, Money.of(249), "cr-1", "4821", null);
    }

    /** Drives a new job into the given status along the normal path. */
    static Job jobIn(JobStatus status) {
        Job job = newJob();
        switch (status) {
            case PENDING_PAYMENT -> { }
            case CONFIRMED -> job.confirm("p-1");
            case ASSIGNED -> { job.confirm("p-1"); job.assign("m-1", "as-1"); }
            case EN_ROUTE -> { job.confirm("p-1"); job.assign("m-1", "as-1"); job.enRoute(); }
            case IN_PROGRESS -> { job.confirm("p-1"); job.assign("m-1", "as-1"); job.enRoute(); job.start("4821"); }
            case COMPLETED -> { job.confirm("p-1"); job.assign("m-1", "as-1"); job.enRoute(); job.start("4821"); job.complete(); }
            case CANCELLED -> job.cancel();
            case EXPIRED -> job.expire();
            case UNFULFILLED -> { job.confirm("p-1"); job.markUnfulfilled(); }
        }
        assertThat(job.status()).isEqualTo(status);
        return job;
    }

    /** Every action, and the statuses it is allowed from. */
    private static final Map<String, Set<JobStatus>> ALLOWED_FROM = Map.of(
            "confirm", EnumSet.of(JobStatus.PENDING_PAYMENT),
            "assign", EnumSet.of(JobStatus.CONFIRMED),
            "unassign", EnumSet.of(JobStatus.ASSIGNED),
            "markUnfulfilled", EnumSet.of(JobStatus.CONFIRMED),
            "expire", EnumSet.of(JobStatus.PENDING_PAYMENT),
            "cancel", EnumSet.of(JobStatus.PENDING_PAYMENT, JobStatus.CONFIRMED, JobStatus.ASSIGNED),
            "enRoute", EnumSet.of(JobStatus.ASSIGNED),
            "start", EnumSet.of(JobStatus.EN_ROUTE),
            "complete", EnumSet.of(JobStatus.IN_PROGRESS));

    private static final Map<String, Consumer<Job>> ACTIONS = Map.of(
            "confirm", job -> job.confirm("p-2"),
            "assign", job -> job.assign("m-2", "as-2"),
            "unassign", Job::unassign,
            "markUnfulfilled", Job::markUnfulfilled,
            "expire", Job::expire,
            "cancel", Job::cancel,
            "enRoute", Job::enRoute,
            "start", job -> job.start("4821"),
            "complete", Job::complete);

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void everyActionIsAllowedOnlyFromItsStatuses(JobStatus from) {
        ACTIONS.forEach((action, run) -> {
            Job job = jobIn(from);
            if (ALLOWED_FROM.get(action).contains(from)) {
                run.accept(job);
            } else {
                assertThatThrownBy(() -> run.accept(job)).as("%s from %s", action, from)
                        .isInstanceOf(InvalidStateTransitionException.class);
                assertThat(job.status()).isEqualTo(from);
            }
        });
    }

    @Test
    void normalScheduledJob() {
        Job job = jobIn(JobStatus.COMPLETED);

        assertThat(job.paymentId()).isEqualTo("p-1");
        assertThat(job.maidId()).isEqualTo("m-1");
        assertThat(job.status().isFinal()).isTrue();
    }

    @Test
    void instantJobWithHeldMaidIsAssignedOnPayment() {
        Job job = newJob();
        job.holdMaid("m-7", "as-7");

        job.confirm("p-1");

        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(job.maidId()).isEqualTo("m-7");
        assertThatThrownBy(() -> jobIn(JobStatus.CONFIRMED).holdMaid("m-7", "as-7"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void maidDropOutGoesBackToWaitingWithoutLosingPayment() {
        Job job = jobIn(JobStatus.ASSIGNED);

        job.unassign();

        assertThat(job.status()).isEqualTo(JobStatus.CONFIRMED);
        assertThat(job.maidId()).isNull();
        assertThat(job.paymentId()).isEqualTo("p-1");
        job.assign("m-2", "as-2");
        assertThat(job.maidId()).isEqualTo("m-2");
    }

    @Test
    void wrongOtpDoesNotStartTheJob() {
        Job job = jobIn(JobStatus.EN_ROUTE);

        assertThatThrownBy(() -> job.start("0000")).isInstanceOf(ValidationException.class);
        assertThat(job.status()).isEqualTo(JobStatus.EN_ROUTE);
    }

    @Test
    void cancellableOnlyBeforeTheMaidSetsOff() {
        assertThat(EnumSet.allOf(JobStatus.class).stream().filter(JobStatus::isCancellable))
                .containsExactlyInAnyOrder(JobStatus.PENDING_PAYMENT, JobStatus.CONFIRMED, JobStatus.ASSIGNED);
        assertThat(EnumSet.allOf(JobStatus.class).stream().filter(JobStatus::isFinal))
                .containsExactlyInAnyOrder(JobStatus.COMPLETED, JobStatus.CANCELLED, JobStatus.EXPIRED,
                        JobStatus.UNFULFILLED);
    }

    @Test
    void otpMustBeFourDigits() {
        TimeSlot slot = TimeSlot.of(LocalDate.of(2026, 11, 23).atTime(9, 0), Duration.ofHours(1));
        assertThatThrownBy(() -> new Job("j", "b", "kor-5", ServiceType.CLEANING, slot, Money.of(249), "cr", "12a4", null))
                .isInstanceOf(ValidationException.class);
    }
}
