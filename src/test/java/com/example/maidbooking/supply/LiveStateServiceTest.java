package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;

class LiveStateServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private final MaidRepository maids = new MaidRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidLiveStateRepository liveStates = new MaidLiveStateRepository();
    private Shift morning;

    private LiveStateService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(IST).toInstant(), IST);
        return new LiveStateService(maids, shifts, liveStates, new InMemoryLockRegistry(), clock, 30);
    }

    @BeforeEach
    void seed() {
        Maid maid = MaidTest.maid("m-1", Set.of(ServiceType.CLEANING));
        maid.activate();
        maids.save(maid);
        morning = shifts.save(new Shift("s-1", "m-1", "kor-5", new TimeSlot(MON.atTime(7, 0), MON.atTime(13, 0))));
    }

    @Test
    void unknownStateIsOffline() {
        assertThat(serviceAt(MON.atTime(6, 0)).get("m-1").status()).isEqualTo(LiveStatus.OFFLINE);
    }

    @Test
    void checkInUpToThirtyMinutesEarlyStartsTheShift() {
        MaidLiveState state = serviceAt(MON.atTime(6, 40)).changeStatus("m-1", LiveStatus.IDLE);

        assertThat(state.status()).isEqualTo(LiveStatus.IDLE);
        assertThat(state.zoneId()).isEqualTo("kor-5");
        assertThat(morning.status()).isEqualTo(ShiftStatus.CHECKED_IN);
    }

    @Test
    void checkInTooEarlyOrWithoutAShiftIsRejected() {
        assertThatThrownBy(() -> serviceAt(MON.atTime(6, 0)).changeStatus("m-1", LiveStatus.IDLE))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no shift");
        assertThatThrownBy(() -> serviceAt(MON.atTime(14, 0)).changeStatus("m-1", LiveStatus.IDLE))
                .isInstanceOf(ConflictException.class);
        assertThat(morning.status()).isEqualTo(ShiftStatus.PLANNED);
    }

    @Test
    void breakThenBackThenCheckOutEndsTheShift() {
        serviceAt(MON.atTime(7, 0)).changeStatus("m-1", LiveStatus.IDLE);
        serviceAt(MON.atTime(10, 0)).changeStatus("m-1", LiveStatus.BREAK);
        serviceAt(MON.atTime(10, 30)).changeStatus("m-1", LiveStatus.IDLE);

        MaidLiveState state = serviceAt(MON.atTime(13, 0)).changeStatus("m-1", LiveStatus.OFFLINE);

        assertThat(state.status()).isEqualTo(LiveStatus.OFFLINE);
        assertThat(state.zoneId()).isNull();
        assertThat(state.updatedAt()).isEqualTo(MON.atTime(13, 0));
        assertThat(morning.status()).isEqualTo(ShiftStatus.ENDED);
    }

    @Test
    void zoneIsKeptAcrossABreak() {
        serviceAt(MON.atTime(7, 0)).changeStatus("m-1", LiveStatus.IDLE);
        MaidLiveState onBreak = serviceAt(MON.atTime(9, 0)).changeStatus("m-1", LiveStatus.BREAK);

        assertThat(onBreak.zoneId()).isEqualTo("kor-5");
        assertThat(liveStates.findByStatusInZone(LiveStatus.BREAK, "kor-5")).hasSize(1);
    }

    @Test
    void illegalMovesAreRejected() {
        LiveStateService service = serviceAt(MON.atTime(7, 0));

        assertThatThrownBy(() -> service.changeStatus("m-1", LiveStatus.BREAK))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage("Cannot set BREAK for maid in state OFFLINE");
        service.changeStatus("m-1", LiveStatus.IDLE);
        assertThatThrownBy(() -> service.changeStatus("m-1", LiveStatus.IDLE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void jobStatusesCannotBeSetByTheMaid() {
        assertThatThrownBy(() -> serviceAt(MON.atTime(7, 0)).changeStatus("m-1", LiveStatus.ON_JOB))
                .isInstanceOf(ValidationException.class).hasMessageContaining("set by jobs");
    }
}
