package com.example.maidbooking.allocation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import com.example.maidbooking.common.TimeSlot;

/**
 * Read-only view of one maid's live assignments, ordered by job start. Build it under the maid's lock,
 * check a candidate job with {@link #fits}, then save the new assignment while still holding the lock.
 */
public class MaidCalendar {

    private final String maidId;
    private final NavigableMap<LocalDateTime, Assignment> byWorkStart = new TreeMap<>();

    public MaidCalendar(String maidId, List<Assignment> liveAssignments) {
        this.maidId = maidId;
        liveAssignments.stream().filter(Assignment::isLive)
                .forEach(a -> byWorkStart.put(a.work().start(), a));
    }

    /**
     * True when the job's blocked time (travel + work) overlaps none of her jobs, and she can still travel
     * from it to her next job in time.
     *
     * @param travelToNext travel time from this job's zone to the next job's zone
     */
    public boolean fits(TimeSlot blocked, Duration travelToNext) {
        boolean overlaps = byWorkStart.values().stream().anyMatch(a -> a.blocked().overlaps(blocked));
        if (overlaps) {
            return false;
        }
        return nextAfter(blocked.end())
                .map(next -> Duration.between(blocked.end(), next.work().start()).compareTo(travelToNext) >= 0)
                .orElse(true);
    }

    /** Her job that ends last at or before {@code time}: where she travels from. */
    public Optional<Assignment> previousBefore(LocalDateTime time) {
        return byWorkStart.headMap(time, false).values().stream()
                .filter(a -> !a.work().end().isAfter(time))
                .reduce((first, second) -> second);
    }

    /** Her first job starting at or after {@code time}. */
    public Optional<Assignment> nextAfter(LocalDateTime time) {
        return Optional.ofNullable(byWorkStart.ceilingEntry(time)).map(Map.Entry::getValue);
    }

    public long jobsOn(LocalDate date) {
        return byWorkStart.keySet().stream().filter(start -> start.toLocalDate().equals(date)).count();
    }

    public String maidId() {
        return maidId;
    }
}
