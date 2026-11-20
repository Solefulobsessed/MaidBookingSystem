package com.example.maidbooking.supply;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.example.maidbooking.common.error.InvalidStateTransitionException;

/**
 * A maid's live status and where she is. Starts OFFLINE. EN_ROUTE and ON_JOB are set by the job
 * lifecycle later; a maid can only change between OFFLINE, IDLE and BREAK herself.
 */
public class MaidLiveState {

    private static final Map<LiveStatus, Set<LiveStatus>> ALLOWED = new EnumMap<>(Map.of(
            LiveStatus.OFFLINE, Set.of(LiveStatus.IDLE),
            LiveStatus.IDLE, Set.of(LiveStatus.BREAK, LiveStatus.OFFLINE, LiveStatus.EN_ROUTE),
            LiveStatus.BREAK, Set.of(LiveStatus.IDLE, LiveStatus.OFFLINE),
            LiveStatus.EN_ROUTE, Set.of(LiveStatus.ON_JOB),
            LiveStatus.ON_JOB, Set.of(LiveStatus.IDLE)));

    private final String maidId;
    private LiveStatus status = LiveStatus.OFFLINE;
    private String zoneId;
    private LocalDateTime updatedAt;

    public MaidLiveState(String maidId) {
        this.maidId = Objects.requireNonNull(maidId, "maidId");
    }

    public void moveTo(LiveStatus target, String zoneId, LocalDateTime at) {
        if (!ALLOWED.get(status).contains(target)) {
            throw new InvalidStateTransitionException("maid", status, "set " + target + " for");
        }
        this.status = target;
        this.zoneId = target == LiveStatus.OFFLINE ? null : Objects.requireNonNullElse(zoneId, this.zoneId);
        this.updatedAt = at;
    }

    public String maidId() { return maidId; }
    public LiveStatus status() { return status; }
    public String zoneId() { return zoneId; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
