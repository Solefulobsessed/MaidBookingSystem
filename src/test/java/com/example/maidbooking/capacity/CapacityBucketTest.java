package com.example.maidbooking.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;

class CapacityBucketTest {

    private static final LocalDateTime NINE = LocalDate.of(2026, 11, 23).atTime(9, 0);
    private static final double FACTOR = 0.85;

    private static CapacityBucket bucketWith(long capacity) {
        CapacityBucket bucket = new CapacityBucket(BucketKey.forService("kor-5", ServiceType.CLEANING, NINE));
        bucket.addCapacity(capacity);
        return bucket;
    }

    @Test
    void keysMustBeOnThirtyMinuteBoundaries() {
        assertThat(BucketKey.forZone("kor-5", NINE.plusMinutes(30)).pool()).isEqualTo(BucketKey.ZONE_POOL);
        assertThat(BucketKey.forService("kor-5", ServiceType.COOKING, NINE).pool()).isEqualTo("COOKING");
        assertThatThrownBy(() -> BucketKey.forZone("kor-5", NINE.plusMinutes(15)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void freeMinutesApplyTheUtilisationFactor() {
        // 4 maids x 30 min = 120 raw minutes; 85% = 102 sellable
        CapacityBucket bucket = bucketWith(120);

        assertThat(bucket.free(FACTOR)).isEqualTo(102);
        assertThat(bucket.free(1.0)).isEqualTo(120);
    }

    @Test
    void reserveAndReleaseExactlyUndoEachOther() {
        CapacityBucket bucket = bucketWith(120);

        bucket.reserve(30, FACTOR);
        bucket.reserve(15, FACTOR);
        assertThat(bucket.free(FACTOR)).isEqualTo(57);
        bucket.release(15);
        bucket.release(30);

        assertThat(bucket.reservedMinutes()).isZero();
        assertThat(bucket.free(FACTOR)).isEqualTo(102);
    }

    @Test
    void cannotReserveMoreThanIsFree() {
        CapacityBucket bucket = bucketWith(120);
        bucket.reserve(90, FACTOR);

        assertThatThrownBy(() -> bucket.reserve(30, FACTOR)).isInstanceOf(IllegalStateException.class);
        assertThat(bucket.reservedMinutes()).isEqualTo(90);
    }

    @Test
    void cannotReleaseMoreThanIsReserved() {
        CapacityBucket bucket = bucketWith(60);
        bucket.reserve(30, FACTOR);

        assertThatThrownBy(() -> bucket.release(45)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void removingCapacityBelowReservationsLeavesTheBucketOverbookedNotNegative() {
        CapacityBucket bucket = bucketWith(60);
        bucket.reserve(30, FACTOR);

        bucket.removeCapacity(60);
        bucket.removeCapacity(30);

        assertThat(bucket.capacityMinutes()).isZero();
        assertThat(bucket.free(FACTOR)).isEqualTo(-30);
    }

    @Test
    void everyChangeBumpsTheVersionAndMinutesMustBePositive() {
        CapacityBucket bucket = bucketWith(60);
        bucket.reserve(30, FACTOR);
        bucket.release(30);

        assertThat(bucket.version()).isEqualTo(3);
        assertThatThrownBy(() -> bucket.addCapacity(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket.reserve(-5, FACTOR)).isInstanceOf(IllegalArgumentException.class);
    }
}
