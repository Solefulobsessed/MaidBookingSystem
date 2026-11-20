package com.example.maidbooking.cancellation;

/** Who caused the cancellation. PLATFORM covers no maid found (UNFULFILLED) and maid no-shows. */
public enum CancelledBy {
    CUSTOMER,
    PLATFORM
}
