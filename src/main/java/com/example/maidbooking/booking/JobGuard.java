package com.example.maidbooking.booking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;

/**
 * Lets one money-moving action (paying, cancelling) run on a job at a time. It refuses instead of waiting, so it
 * never blocks while zone and maid locks are taken and the zone → maid → job order is kept.
 */
@Component
public class JobGuard {

    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    /** @throws ConflictException REQUEST_IN_PROGRESS if another action is running on this job */
    public <T> T exclusive(String jobId, Supplier<T> action) {
        if (!busy.add(jobId)) {
            throw new ConflictException(ErrorCode.REQUEST_IN_PROGRESS,
                    "Job " + jobId + " is busy with another payment or cancellation", Map.of("jobId", jobId));
        }
        try {
            return action.get();
        } finally {
            busy.remove(jobId);
        }
    }
}
