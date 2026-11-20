package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

class ShiftTest {

    static final LocalDate MON = LocalDate.of(2026, 11, 23);

    static Shift shift(String id, String maidId, String zone, int fromHour, int toHour) {
        return new Shift(id, maidId, zone, new TimeSlot(MON.atTime(fromHour, 0), MON.atTime(toHour, 0)));
    }

    @Test
    void newShiftIsPlannedAndAddsCapacity() {
        Shift shift = shift("s-1", "m-1", "kor-5", 7, 13);

        assertThat(shift.status()).isEqualTo(ShiftStatus.PLANNED);
        assertThat(shift.addsCapacity()).isTrue();
        assertThat(shift.date()).isEqualTo(MON);
    }

    @Test
    void normalDayCheckInThenEnd() {
        Shift shift = shift("s-1", "m-1", "kor-5", 7, 13);

        shift.checkIn();
        assertThat(shift.addsCapacity()).isTrue();
        shift.end();
        assertThat(shift.status()).isEqualTo(ShiftStatus.ENDED);
        assertThat(shift.addsCapacity()).isFalse();
    }

    @Test
    void absentAndCancelledStopCountingTowardsCapacity() {
        Shift absent = shift("s-1", "m-1", "kor-5", 7, 13);
        Shift cancelled = shift("s-2", "m-1", "kor-5", 14, 18);

        absent.markAbsent();
        cancelled.cancel();

        assertThat(absent.addsCapacity()).isFalse();
        assertThat(cancelled.addsCapacity()).isFalse();
    }

    @Test
    void illegalTransitionsThrow() {
        Shift shift = shift("s-1", "m-1", "kor-5", 7, 13);

        assertThatThrownBy(shift::end).isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage("Cannot end Shift in state PLANNED");
        shift.checkIn();
        assertThatThrownBy(shift::cancel).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(shift::markAbsent).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(shift::checkIn).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void lengthMustBeTwoToTenHours() {
        assertThat(shift("s-1", "m-1", "kor-5", 7, 9).window().duration().toHours()).isEqualTo(2);
        assertThat(shift("s-2", "m-1", "kor-5", 7, 17).window().duration().toHours()).isEqualTo(10);
        assertThatThrownBy(() -> new Shift("s-3", "m-1", "kor-5",
                new TimeSlot(MON.atTime(7, 0), MON.atTime(8, 30)))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> shift("s-4", "m-1", "kor-5", 7, 18)).isInstanceOf(ValidationException.class);
    }

    @Test
    void cannotRunPastMidnight() {
        LocalDateTime late = MON.atTime(20, 0);

        assertThatThrownBy(() -> new Shift("s-1", "m-1", "kor-5", new TimeSlot(late, late.plusHours(5))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("same day");
    }

    @Test
    void changedEventsCarryTheShiftDetails() {
        Shift shift = shift("s-1", "m-1", "kor-5", 7, 13);

        assertThat(ShiftChanged.added(shift)).isEqualTo(new ShiftChanged("s-1", "m-1", "kor-5", shift.window(),
                ShiftChanged.Change.CAPACITY_ADDED));
        assertThat(ShiftChanged.removed(shift).change()).isEqualTo(ShiftChanged.Change.CAPACITY_REMOVED);
    }
}
