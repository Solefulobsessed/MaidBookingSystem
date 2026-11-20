package com.example.maidbooking.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.supply.ServiceType;

/**
 * Many customers booking the same slot at the same instant must never overbook the zone.
 * Repeated 20 times because races are probabilistic.
 */
class CapacityConcurrencyTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot NINE_TO_TEN = TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1));
    private static final int THREADS = 100;

    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final CapacityReservationRepository reservations = new CapacityReservationRepository();
    private final CapacityService service = new CapacityService(buckets, reservations, new InMemoryLockRegistry(),
            event -> { }, new MutableClock(MON.atTime(8, 0)), new CapacityProperties(1.0));
    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void shutDown() throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    /** Adds {@code maids x 30} minutes per bucket from 09:00 to 10:00 to the given pools. */
    private void maidsOnShift(int maids, String... pools) {
        for (LocalDateTime t = MON.atTime(9, 0); t.isBefore(MON.atTime(10, 0)); t = t.plusMinutes(30)) {
            for (String pool : pools) {
                buckets.getOrCreate(new BucketKey("kor-5", pool, t)).addCapacity(maids * 30L);
            }
        }
    }

    /** Starts every task at the same moment and counts successful reservations; any other failure fails the test. */
    private int raceAll(List<Callable<CapacityReservation>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Callable<CapacityReservation> task : tasks) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    task.call();
                    return true;
                } catch (ConflictException full) {
                    assertThat(full.code()).isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        return succeeded;
    }

    private void assertNoBucketOverbooked() {
        assertThat(buckets.findAll()).allSatisfy(bucket -> {
            assertThat(bucket.reservedMinutes()).isLessThanOrEqualTo(bucket.capacityMinutes());
            assertThat(bucket.free(1.0)).isGreaterThanOrEqualTo(0);
        });
    }

    @RepeatedTest(20)
    void hundredCustomersTenMaidsExactlyTenBookings() throws Exception {
        maidsOnShift(10, "CLEANING", BucketKey.ZONE_POOL);
        List<Callable<CapacityReservation>> customers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String jobId = "j-" + i;
            customers.add(() -> service.reserve("kor-5", ServiceType.CLEANING, NINE_TO_TEN, jobId, null));
        }

        int succeeded = raceAll(customers);

        assertThat(succeeded).isEqualTo(10);
        assertThat(reservations.findAll()).hasSize(10);
        assertThat(buckets.getOrCreate(BucketKey.forZone("kor-5", MON.atTime(9, 30))).reservedMinutes()).isEqualTo(300);
        assertNoBucketOverbooked();
    }

    @RepeatedTest(20)
    void cleaningAndCookingCompeteForTheSameMultiSkilledMaids() throws Exception {
        // 5 maids who both clean and cook: each service pool shows 5 maids, but the zone pool only 5 in total.
        maidsOnShift(5, "CLEANING", "COOKING", BucketKey.ZONE_POOL);
        List<Callable<CapacityReservation>> customers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            ServiceType service = i % 2 == 0 ? ServiceType.CLEANING : ServiceType.COOKING;
            String jobId = "j-" + i;
            customers.add(() -> this.service.reserve("kor-5", service, NINE_TO_TEN, jobId, null));
        }

        int succeeded = raceAll(customers);

        assertThat(succeeded).isEqualTo(5);
        assertNoBucketOverbooked();
    }

    @RepeatedTest(20)
    void releasesRacingWithNewBookingsNeverLoseOrInventMinutes() throws Exception {
        maidsOnShift(10, "CLEANING", BucketKey.ZONE_POOL);
        List<CapacityReservation> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            existing.add(service.reserve("kor-5", ServiceType.CLEANING, NINE_TO_TEN, "old-" + i, null));
        }
        List<Callable<CapacityReservation>> tasks = new ArrayList<>();
        for (CapacityReservation old : existing.subList(0, 5)) {
            tasks.add(() -> service.release(old.id()));
        }
        for (int i = 0; i < 50; i++) {
            String jobId = "new-" + i;
            tasks.add(() -> service.reserve("kor-5", ServiceType.CLEANING, NINE_TO_TEN, jobId, null));
        }

        raceAll(tasks);

        long live = reservations.findAll().stream().filter(r -> r.status() != ReservationStatus.RELEASED).count();
        assertThat(live).as("never more live bookings than maids").isLessThanOrEqualTo(10);
        assertThat(buckets.getOrCreate(BucketKey.forZone("kor-5", MON.atTime(9, 0))).reservedMinutes())
                .as("bucket agrees with live reservations").isEqualTo(live * 30);
        assertNoBucketOverbooked();
    }
}
