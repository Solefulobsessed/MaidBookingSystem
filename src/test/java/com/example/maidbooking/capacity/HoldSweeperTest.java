package com.example.maidbooking.capacity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class HoldSweeperTest {

    @Test
    void sweepDelegatesToTheCapacityService() {
        CapacityService capacityService = mock(CapacityService.class);

        new HoldSweeper(capacityService).sweep();

        verify(capacityService).releaseExpiredHolds();
    }
}
