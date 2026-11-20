package com.example.maidbooking.capacity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.maidbooking.common.ManualJob;

/** Releases lapsed holds even in zones nobody is booking, so the slot grid stays accurate. */
@Component
public class HoldSweeper implements ManualJob {

    private final CapacityService capacityService;

    public HoldSweeper(CapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @Scheduled(fixedDelayString = "${app.capacity.hold-sweep-interval-ms:60000}")
    public void sweep() {
        capacityService.releaseExpiredHolds();
    }

    @Override
    public String name() {
        return "hold-sweeper";
    }

    /** @return how many lapsed holds were released */
    @Override
    public Object runNow() {
        return capacityService.releaseExpiredHolds();
    }
}
