package com.example.maidbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;

class IdempotencyStoreTest {

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 23, 9, 0));
    private final IdempotencyStore store = new IdempotencyStore(clock, 24);
    private final AtomicInteger charges = new AtomicInteger();

    private String charge() {
        return "payment-" + charges.incrementAndGet();
    }

    @Test
    void firstCallRunsAndRepeatReplaysTheSameResult() {
        IdempotentResult<String> first = store.execute("key-1", "hash-a", this::charge);
        IdempotentResult<String> second = store.execute("key-1", "hash-a", this::charge);

        assertThat(first).isEqualTo(new IdempotentResult<>("payment-1", false));
        assertThat(second).isEqualTo(new IdempotentResult<>("payment-1", true));
        assertThat(charges).hasValue(1);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        store.execute("key-1", "hash-a", this::charge);

        assertThatThrownBy(() -> store.execute("key-1", "hash-b", this::charge))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(charges).hasValue(1);
    }

    @Test
    void businessErrorIsReplayedWithoutRunningAgain() {
        ConflictException declined = new ConflictException(ErrorCode.HOLD_EXPIRED, "Hold expired", Map.of());
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> store.execute("key-1", "hash-a", () -> {
                attempts.incrementAndGet();
                throw declined;
            })).isSameAs(declined);
        }
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unexpectedErrorReleasesTheKeySoARetryRuns() {
        assertThatThrownBy(() -> store.execute("key-1", "hash-a", () -> {
            throw new IllegalStateException("gateway timeout");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(store.execute("key-1", "hash-a", this::charge).value()).isEqualTo("payment-1");
    }

    @Test
    void repeatWhileFirstIsStillRunningIsRejected() {
        store.execute("key-1", "hash-a", () -> {
            assertThatThrownBy(() -> store.execute("key-1", "hash-a", this::charge))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
            return charge();
        });

        assertThat(charges).hasValue(1);
    }

    @Test
    void parallelRequestsWithTheSameKeyChargeOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    return store.execute("key-1", "hash-a", this::charge).value();
                } catch (ConflictException inProgress) {
                    return "in-progress";
                }
            }));
        }
        start.countDown();
        for (Future<String> result : results) {
            assertThat(result.get()).isIn("payment-1", "in-progress");
        }
        pool.shutdown();

        assertThat(charges).hasValue(1);
    }

    @Test
    void afterTheTtlTheKeyCanBeReused() {
        store.execute("key-1", "hash-a", this::charge);
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        IdempotentResult<String> later = store.execute("key-1", "hash-b", this::charge);

        assertThat(later).isEqualTo(new IdempotentResult<>("payment-2", false));
    }

    @Test
    void purgeExpiredDropsOldRecordsOnly() {
        store.execute("old", "h", this::charge);
        clock.advance(Duration.ofHours(25));
        store.execute("new", "h", this::charge);

        store.purgeExpired();

        assertThat(store.execute("new", "h", this::charge).replayed()).isTrue();
        assertThat(store.execute("old", "h", this::charge).replayed()).isFalse();
    }

    @Test
    void blankKeyIsAValidationError() {
        assertThatThrownBy(() -> store.execute(" ", "h", this::charge)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.execute(null, "h", this::charge)).isInstanceOf(ValidationException.class);
    }

    @Test
    void fingerprintIsStableAndDistinguishesRequests() {
        String a = IdempotencyStore.fingerprint("job=j-1|method=UPI|vpa=user@okbank");

        assertThat(a).hasSize(64).isEqualTo(IdempotencyStore.fingerprint("job=j-1|method=UPI|vpa=user@okbank"));
        assertThat(a).isNotEqualTo(IdempotencyStore.fingerprint("job=j-1|method=CARD"));
    }
}
