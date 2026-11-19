package com.example.maidbooking.common.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class InMemoryLockRegistryTest {

    private final InMemoryLockRegistry registry = new InMemoryLockRegistry();

    @Test
    void keysSortZoneThenMaidThenJobThenId() {
        List<LockKey> keys = new ArrayList<>(List.of(
                LockKey.job("j-1"), LockKey.maid("m-2"), LockKey.zone("z-1"), LockKey.maid("m-1")));
        Collections.sort(keys);

        assertThat(keys).containsExactly(
                LockKey.zone("z-1"), LockKey.maid("m-1"), LockKey.maid("m-2"), LockKey.job("j-1"));
    }

    @Test
    void sameKeyIsMutuallyExclusiveAcrossThreads() throws Exception {
        int threads = 16;
        int incrementsEach = 500;
        int[] counter = {0};
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < incrementsEach; i++) {
                    registry.runWith(LockKey.zone("z-1"), () -> counter[0]++);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(counter[0]).isEqualTo(threads * incrementsEach);
    }

    @Test
    void differentKeysDoNotBlockEachOther() throws Exception {
        CountDownLatch insideZoneA = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> registry.runWith(LockKey.zone("z-a"), () -> {
            insideZoneA.countDown();
            await(release);
        }));
        holder.start();
        insideZoneA.await();

        AtomicInteger ran = new AtomicInteger();
        registry.runWith(LockKey.zone("z-b"), ran::incrementAndGet);
        release.countDown();
        holder.join();

        assertThat(ran).hasValue(1);
    }

    @Test
    void forwardOrderAndReentryAreAllowed() {
        String result = registry.callWith(LockKey.zone("z-1"),
                () -> registry.callWith(LockKey.maid("m-1"),
                        () -> registry.callWith(LockKey.zone("z-1"), () -> "ok")));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void backwardOrderIsRejected() {
        assertThatThrownBy(() -> registry.runWith(LockKey.maid("m-1"),
                () -> registry.runWith(LockKey.zone("z-1"), () -> { })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Lock order violation");
    }

    @Test
    void callWithAllSortsKeysWhateverTheInputOrder() {
        String result = registry.callWithAll(
                Set.of(LockKey.job("j-1"), LockKey.zone("z-1"), LockKey.maid("m-1")), () -> "done");

        assertThat(result).isEqualTo("done");
    }

    @Test
    void locksAreReleasedWhenTheActionThrows() throws Exception {
        assertThatThrownBy(() -> registry.runWith(LockKey.maid("m-1"), () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);

        AtomicInteger ran = new AtomicInteger();
        Thread other = new Thread(() -> registry.runWith(LockKey.maid("m-1"), ran::incrementAndGet));
        other.start();
        other.join(2000);
        assertThat(ran).hasValue(1);
        registry.runWith(LockKey.zone("z-1"), () -> { });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
