package com.example.maidbooking.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.maidbooking.common.error.ValidationException;

/**
 * A half-open interval {@code [start, end)} of local time. Back-to-back slots such as
 * 09:00-10:00 and 10:00-11:00 do not overlap.
 */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

    private static final int MINUTES_PER_DAY = 24 * 60;

    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new ValidationException("Slot start " + start + " must be before end " + end);
        }
    }

    public static TimeSlot of(LocalDateTime start, Duration duration) {
        return new TimeSlot(start, start.plus(duration));
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(TimeSlot other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    /** This slot extended earlier by {@code buffer}, e.g. to reserve travel time before a job. */
    public TimeSlot withBufferBefore(Duration buffer) {
        return new TimeSlot(start.minus(buffer), end);
    }

    /**
     * Minutes this slot occupies in each fixed-size bucket it touches, keyed by bucket start and in
     * time order. Buckets are aligned to midnight, so 30-minute buckets start at :00 and :30.
     */
    public Map<LocalDateTime, Long> minutesPerBucket(int bucketMinutes) {
        if (bucketMinutes <= 0 || MINUTES_PER_DAY % bucketMinutes != 0) {
            throw new ValidationException("Bucket size must divide a day evenly: " + bucketMinutes);
        }
        Map<LocalDateTime, Long> minutes = new LinkedHashMap<>();
        for (LocalDateTime bucket = bucketStart(start, bucketMinutes); bucket.isBefore(end);
                bucket = bucket.plusMinutes(bucketMinutes)) {
            LocalDateTime from = start.isAfter(bucket) ? start : bucket;
            LocalDateTime bucketEnd = bucket.plusMinutes(bucketMinutes);
            LocalDateTime to = end.isBefore(bucketEnd) ? end : bucketEnd;
            minutes.put(bucket, Duration.between(from, to).toMinutes());
        }
        return Collections.unmodifiableMap(minutes);
    }

    private static LocalDateTime bucketStart(LocalDateTime time, int bucketMinutes) {
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        return time.truncatedTo(ChronoUnit.DAYS).plusMinutes((long) (minuteOfDay / bucketMinutes) * bucketMinutes);
    }
}
