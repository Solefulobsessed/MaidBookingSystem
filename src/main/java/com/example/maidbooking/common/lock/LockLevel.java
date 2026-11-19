package com.example.maidbooking.common.lock;

/** Lock levels in the only order they may be acquired: zone, then maid, then job. */
public enum LockLevel {
    ZONE,
    MAID,
    JOB
}
