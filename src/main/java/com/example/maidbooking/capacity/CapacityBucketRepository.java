package com.example.maidbooking.capacity;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class CapacityBucketRepository extends InMemoryRepository<CapacityBucket, BucketKey> {

    public CapacityBucketRepository() {
        super("Capacity bucket", CapacityBucket::key);
    }

    /** The bucket for this key, created empty if it doesn't exist yet. Call under the zone lock. */
    public CapacityBucket getOrCreate(BucketKey key) {
        return findById(key).orElseGet(() -> save(new CapacityBucket(key)));
    }

    public List<CapacityBucket> findByZonePoolAndDate(String zoneId, String pool, LocalDate date) {
        return findAllMatching(b -> b.key().zoneId().equals(zoneId) && b.key().pool().equals(pool)
                && b.key().start().toLocalDate().equals(date))
                .stream().sorted(Comparator.comparing(b -> b.key().start())).toList();
    }
}
