package com.example.maidbooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

class MaidCalendarTest {

    static final LocalDate MON = LocalDate.of(2026, 11, 23);
    static final Duration SAME_ZONE = Duration.ofMinutes(15);
    static final Duration NEIGHBOUR = Duration.ofMinutes(30);

    static TimeSlot work(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
    }

    static Assignment assignment(String id, String from, String to, AssignmentStatus status) {
        TimeSlot work = work(from, to);
        return new Assignment(id, "j-" + id, "m-1", "s-1", "kor-5", work, work.withBufferBefore(SAME_ZONE), status);
    }

    private final Assignment nine = assignment("a-9", "09:00", "10:00", AssignmentStatus.ACTIVE);
    private final Assignment noon = assignment("a-12", "12:00", "13:00", AssignmentStatus.ACTIVE);
    private final MaidCalendar calendar = new MaidCalendar("m-1", List.of(noon, nine));

    @Test
    void jobBetweenTwoOthersFits() {
        assertThat(calendar.fits(work("10:15", "11:15").withBufferBefore(SAME_ZONE), SAME_ZONE)).isTrue();
    }

    @Test
    void travelOverlappingAnEarlierJobDoesNotFit() {
        assertThat(calendar.fits(work("10:00", "11:00").withBufferBefore(SAME_ZONE), SAME_ZONE)).isFalse();
        assertThat(calendar.fits(work("09:30", "10:30").withBufferBefore(SAME_ZONE), SAME_ZONE)).isFalse();
    }

    @Test
    void mustLeaveTimeToReachTheNextJob() {
        TimeSlot elevenToQuarterTo = work("11:00", "11:45").withBufferBefore(SAME_ZONE);

        assertThat(calendar.fits(elevenToQuarterTo, SAME_ZONE)).as("15 min gap, 15 min travel").isTrue();
        assertThat(calendar.fits(elevenToQuarterTo, NEIGHBOUR)).as("15 min gap, 30 min travel").isFalse();
    }

    @Test
    void releasedAssignmentsDoNotBlock() {
        Assignment cancelled = assignment("a-x", "10:15", "11:15", AssignmentStatus.ACTIVE);
        cancelled.release();
        MaidCalendar withCancelled = new MaidCalendar("m-1", List.of(nine, cancelled, noon));

        assertThat(withCancelled.fits(work("10:15", "11:15").withBufferBefore(SAME_ZONE), SAME_ZONE)).isTrue();
        assertThat(withCancelled.jobsOn(MON)).isEqualTo(2);
    }

    @Test
    void neighboursInTime() {
        assertThat(calendar.previousBefore(MON.atTime(12, 0))).contains(nine);
        assertThat(calendar.previousBefore(MON.atTime(9, 30))).as("still working").isEmpty();
        assertThat(calendar.nextAfter(MON.atTime(10, 0))).contains(noon);
        assertThat(calendar.nextAfter(MON.atTime(13, 0))).isEmpty();
        assertThat(calendar.jobsOn(MON)).isEqualTo(2);
        assertThat(calendar.jobsOn(MON.plusDays(1))).isZero();
    }

    @Test
    void emptyDayFitsAnything() {
        assertThat(new MaidCalendar("m-2", List.of()).fits(work("07:00", "08:00"), NEIGHBOUR)).isTrue();
    }

    @Test
    void assignmentLifecycle() {
        Assignment instant = assignment("a-1", "09:00", "10:00", AssignmentStatus.HELD);

        instant.activate();
        assertThat(instant.status()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThatThrownBy(instant::activate).isInstanceOf(InvalidStateTransitionException.class);
        instant.release();
        assertThat(instant.isLive()).isFalse();
        assertThatThrownBy(instant::release).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void assignmentShapeIsValidated() {
        TimeSlot job = work("09:00", "10:00");

        assertThatThrownBy(() -> new Assignment("a", "j", "m-1", "s-1", "kor-5", job, work("08:45", "10:15"),
                AssignmentStatus.ACTIVE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Assignment("a", "j", "m-1", "s-1", "kor-5", job, job, AssignmentStatus.RELEASED))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void repositoryFindsOnlyLiveAssignments() {
        AssignmentRepository repo = new AssignmentRepository();
        Assignment released = assignment("a-r", "14:00", "15:00", AssignmentStatus.ACTIVE);
        released.release();
        repo.save(nine);
        repo.save(released);

        assertThat(repo.findLiveByMaid("m-1")).containsExactly(nine);
        assertThat(repo.findLiveByReference("j-a-9")).contains(nine);
        assertThat(repo.findLiveByReference("j-a-r")).isEmpty();
    }
}
