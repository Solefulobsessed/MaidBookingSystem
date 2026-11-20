package com.example.maidbooking.capacity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases lapsed holds even in zones nobody is booking, so the slot grid stays accurate. */
@Component
public class HoldSweeper {

    private final CapacityService capacityService;

    public HoldSweeper(CapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @Scheduled(fixedDelayString = "${app.capacity.hold-sweep-interval-ms:60000}")
    public void sweep() {
        capacityService.releaseExpiredHolds();
    }
}
