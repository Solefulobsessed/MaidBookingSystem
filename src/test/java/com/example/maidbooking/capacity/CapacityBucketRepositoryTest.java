package com.example.maidbooking.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.supply.ServiceType;

class CapacityBucketRepositoryTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private final CapacityBucketRepository repo = new CapacityBucketRepository();

    @Test
    void getOrCreateReturnsTheSameBucket() {
        BucketKey key = BucketKey.forZone("kor-5", MON.atTime(9, 0));

        CapacityBucket first = repo.getOrCreate(key);
        first.addCapacity(30);

        assertThat(repo.getOrCreate(key)).isSameAs(first);
        assertThat(repo.getOrCreate(key).capacityMinutes()).isEqualTo(30);
    }

    @Test
    void findsOneDayOfOnePoolInTimeOrder() {
        LocalDateTime nine = MON.atTime(9, 0);
        repo.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, nine.plusMinutes(30)));
        repo.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, nine));
        repo.getOrCreate(BucketKey.forService("kor-5", ServiceType.COOKING, nine));
        repo.getOrCreate(BucketKey.forZone("kor-5", nine));
        repo.getOrCreate(BucketKey.forService("kor-5", ServiceType.CLEANING, nine.plusDays(1)));
        repo.getOrCreate(BucketKey.forService("hsr-1", ServiceType.CLEANING, nine));

        assertThat(repo.findByZonePoolAndDate("kor-5", "CLEANING", MON))
                .extracting(b -> b.key().start()).containsExactly(nine, nine.plusMinutes(30));
    }

    @Test
    void propertiesDefaultAndBounds() {
        assertThat(new CapacityProperties(null).utilisationFactor()).isEqualTo(0.85);
        assertThat(new CapacityProperties(1.0).utilisationFactor()).isEqualTo(1.0);
        assertThatThrownBy(() -> new CapacityProperties(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityProperties(1.2)).isInstanceOf(IllegalArgumentException.class);
    }
}
