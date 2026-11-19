package com.example.maidbooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.maidbooking.common.error.ValidationException;

class TimeSlotTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 23, 0, 0);

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(at(from), at(to));
    }

    private static LocalDateTime at(String hhmm) {
        String[] parts = hhmm.split(":");
        return DAY.plusHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
    }

    @ParameterizedTest(name = "09:00-10:00 vs {0}-{1} overlaps={2}")
    @CsvSource({
            "08:00, 09:00, false",   // ends exactly at start
            "10:00, 11:00, false",   // starts exactly at end
            "08:30, 09:30, true",    // overlaps start
            "09:30, 10:30, true",    // overlaps end
            "09:15, 09:45, true",    // contained
            "08:00, 11:00, true",    // contains
            "09:00, 10:00, true",    // identical
            "11:00, 12:00, false"    // disjoint
    })
    void overlapIsHalfOpen(String from, String to, boolean expected) {
        TimeSlot base = slot("09:00", "10:00");
        TimeSlot other = slot(from, to);

        assertThat(base.overlaps(other)).isEqualTo(expected);
        assertThat(other.overlaps(base)).isEqualTo(expected);
    }

    @Test
    void containsIncludesTouchingEdges() {
        TimeSlot shift = slot("07:00", "13:00");

        assertThat(shift.contains(slot("07:00", "08:00"))).isTrue();
        assertThat(shift.contains(slot("12:00", "13:00"))).isTrue();
        assertThat(shift.contains(slot("12:30", "13:30"))).isFalse();
    }

    @Test
    void ofAndDurationAgree() {
        TimeSlot slot = TimeSlot.of(at("09:00"), Duration.ofMinutes(90));

        assertThat(slot.end()).isEqualTo(at("10:30"));
        assertThat(slot.duration()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void bufferExtendsStartOnly() {
        TimeSlot withTravel = slot("09:00", "10:00").withBufferBefore(Duration.ofMinutes(15));

        assertThat(withTravel).isEqualTo(slot("08:45", "10:00"));
    }

    @Test
    void rejectsEmptyOrInvertedSlots() {
        assertThatThrownBy(() -> slot("09:00", "09:00")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> slot("10:00", "09:00")).isInstanceOf(ValidationException.class);
    }

    @Test
    void alignedSlotFillsWholeBuckets() {
        assertThat(slot("09:00", "10:00").minutesPerBucket(30))
                .containsExactly(entry(at("09:00"), 30L), entry(at("09:30"), 30L));
    }

    @Test
    void travelBufferSpillsIntoPreviousBucket() {
        TimeSlot job = slot("09:00", "10:00").withBufferBefore(Duration.ofMinutes(15));

        assertThat(job.minutesPerBucket(30))
                .containsExactly(entry(at("08:30"), 15L), entry(at("09:00"), 30L), entry(at("09:30"), 30L));
    }

    @Test
    void shortSlotInsideOneBucket() {
        assertThat(slot("09:10", "09:20").minutesPerBucket(30)).containsExactly(entry(at("09:00"), 10L));
    }

    @Test
    void bucketsContinueAcrossMidnight() {
        TimeSlot late = slot("23:30", "23:59");
        TimeSlot overnight = new TimeSlot(at("23:45"), DAY.plusDays(1).plusMinutes(15));

        assertThat(late.minutesPerBucket(30)).containsExactly(entry(at("23:30"), 29L));
        assertThat(overnight.minutesPerBucket(30))
                .containsExactly(entry(at("23:30"), 15L), entry(DAY.plusDays(1), 15L));
    }

    @Test
    void bucketMinutesAlwaysSumToDuration() {
        TimeSlot odd = slot("08:47", "11:13");

        assertThat(odd.minutesPerBucket(30).values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(odd.duration().toMinutes());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -30, 7, 25})
    void rejectsBucketSizesThatDoNotDivideADay(int size) {
        assertThatThrownBy(() -> slot("09:00", "10:00").minutesPerBucket(size))
                .isInstanceOf(ValidationException.class);
    }
}
