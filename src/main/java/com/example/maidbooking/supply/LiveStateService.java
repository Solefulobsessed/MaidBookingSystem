package com.example.maidbooking.supply;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.LockKey;
import com.example.maidbooking.common.lock.LockRegistry;

/** Check in, breaks and check out. Checking in starts today's shift; checking out ends it. */
@Service
public class LiveStateService {

    private static final Set<LiveStatus> SETTABLE_BY_MAID = Set.of(LiveStatus.IDLE, LiveStatus.BREAK, LiveStatus.OFFLINE);

    private final MaidRepository maids;
    private final ShiftRepository shifts;
    private final MaidLiveStateRepository liveStates;
    private final LockRegistry locks;
    private final Clock clock;
    private final Duration earlyCheckIn;

    public LiveStateService(MaidRepository maids, ShiftRepository shifts, MaidLiveStateRepository liveStates,
                            LockRegistry locks, Clock clock,
                            @Value("${app.shift.early-check-in-minutes:30}") long earlyCheckInMinutes) {
        this.maids = maids;
        this.shifts = shifts;
        this.liveStates = liveStates;
        this.locks = locks;
        this.clock = clock;
        this.earlyCheckIn = Duration.ofMinutes(earlyCheckInMinutes);
    }

    public MaidLiveState changeStatus(String maidId, LiveStatus target) {
        maids.getById(maidId);
        if (!SETTABLE_BY_MAID.contains(target)) {
            throw new ValidationException("A maid can only set IDLE, BREAK or OFFLINE; " + target + " is set by jobs");
        }
        return locks.callWith(LockKey.maid(maidId), () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            MaidLiveState state = liveStates.getOrOffline(maidId);
            String zoneId = null;
            if (state.status() == LiveStatus.OFFLINE && target == LiveStatus.IDLE) {
                Shift shift = currentPlannedShift(maidId, now);
                shift.checkIn();
                zoneId = shift.zoneId();
            }
            state.moveTo(target, zoneId, now);
            if (target == LiveStatus.OFFLINE) {
                shifts.findByMaidAndDate(maidId, now.toLocalDate()).stream()
                        .filter(s -> s.status() == ShiftStatus.CHECKED_IN).forEach(Shift::end);
            }
            return liveStates.save(state);
        });
    }

    public MaidLiveState get(String maidId) {
        maids.getById(maidId);
        return liveStates.getOrOffline(maidId);
    }

    private Shift currentPlannedShift(String maidId, LocalDateTime now) {
        return shifts.findByMaidAndDate(maidId, now.toLocalDate()).stream()
                .filter(s -> s.status() == ShiftStatus.PLANNED)
                .filter(s -> !now.isBefore(s.window().start().minus(earlyCheckIn)) && now.isBefore(s.window().end()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(ErrorCode.INVALID_STATE,
                        "Maid " + maidId + " has no shift to check in to right now", Map.of("maidId", maidId)));
    }
}
