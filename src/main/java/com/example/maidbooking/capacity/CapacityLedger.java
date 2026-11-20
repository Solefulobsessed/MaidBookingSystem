package com.example.maidbooking.capacity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.maidbooking.common.lock.LockKey;
import com.example.maidbooking.common.lock.LockRegistry;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.ShiftChanged;

/**
 * Keeps capacity buckets in step with shifts. A shift adds its minutes to the pool of every service
 * the maid offers, and once to the zone-wide pool; cancelling it removes the same minutes.
 */
@Component
public class CapacityLedger {

    private final CapacityBucketRepository buckets;
    private final MaidRepository maids;
    private final LockRegistry locks;

    public CapacityLedger(CapacityBucketRepository buckets, MaidRepository maids, LockRegistry locks) {
        this.buckets = buckets;
        this.maids = maids;
        this.locks = locks;
    }

    @EventListener
    public void onShiftChanged(ShiftChanged event) {
        List<String> pools = poolsFor(event.maidId());
        Map<LocalDateTime, Long> minutes = event.window().minutesPerBucket(BucketKey.BUCKET_MINUTES);
        boolean adding = event.change() == ShiftChanged.Change.CAPACITY_ADDED;
        locks.runWith(LockKey.zone(event.zoneId()), () -> minutes.forEach((start, mins) -> {
            for (String pool : pools) {
                CapacityBucket bucket = buckets.getOrCreate(new BucketKey(event.zoneId(), pool, start));
                if (adding) {
                    bucket.addCapacity(mins);
                } else {
                    bucket.removeCapacity(mins);
                }
            }
        }));
    }

    private List<String> poolsFor(String maidId) {
        List<String> pools = new ArrayList<>();
        maids.getById(maidId).skills().stream().map(ServiceType::name).sorted().forEach(pools::add);
        pools.add(BucketKey.ZONE_POOL);
        return pools;
    }
}
