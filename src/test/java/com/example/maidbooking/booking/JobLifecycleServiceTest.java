package com.example.maidbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.cancellation.CancellationProperties;
import com.example.maidbooking.cancellation.CancelledBy;
import com.example.maidbooking.cancellation.TimeBasedCancellationPolicy;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.LiveStateService;
import com.example.maidbooking.supply.LiveStatus;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidLiveStateRepository;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;

class JobLifecycleServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private final MutableClock clock = new MutableClock(MON.atTime(8, 0));
    private final MaidRepository maids = new MaidRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidLiveStateRepository liveStateRepository = new MaidLiveStateRepository();
    private final LiveStateService liveStates = new LiveStateService(maids, shifts, liveStateRepository,
            new InMemoryLockRegistry(), clock, 30);
    private final JobRepository jobs = new JobRepository();
    private final JobLifecycleService lifecycle = new JobLifecycleService(jobs, liveStates, new JobGuard());
    private Job job;

    @BeforeEach
    void setUp() {
        Maid maid = new Maid("m-01", "Asha", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING), null,
                Tier.STANDARD, "hsr-2", 4.5);
        maid.activate();
        maids.save(maid);
        shifts.save(new Shift("s-1", "m-01", "hsr-2", new TimeSlot(MON.atTime(7, 0), MON.atTime(13, 0))));
        job = jobs.save(new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING,
                new TimeSlot(MON.atTime(9, 0), MON.atTime(10, 0)), Money.of(249), "cr-1", "4821", null));
        job.confirm("p-1");
        job.assign("m-01", "as-1");
    }

    private LiveStatus maidStatus() {
        return liveStates.get("m-01").status();
    }

    @Test
    void aVisitGoesOnTheWayThenStartedThenDoneAndTheMaidIsFreeInThatZone() {
        liveStates.changeStatus("m-01", LiveStatus.IDLE);

        assertThat(lifecycle.enRoute("j-1").status()).isEqualTo(JobStatus.EN_ROUTE);
        assertThat(maidStatus()).isEqualTo(LiveStatus.EN_ROUTE);
        assertThat(lifecycle.start("j-1", "4821").status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(maidStatus()).isEqualTo(LiveStatus.ON_JOB);
        assertThat(lifecycle.complete("j-1").status()).isEqualTo(JobStatus.COMPLETED);

        assertThat(maidStatus()).isEqualTo(LiveStatus.IDLE);
        assertThat(liveStates.get("m-01").zoneId()).as("she is now in the customer's zone").isEqualTo("kor-5");
    }

    @Test
    void aWrongOtpIsRejectedAndChangesNothing() {
        liveStates.changeStatus("m-01", LiveStatus.IDLE);
        lifecycle.enRoute("j-1");

        assertThatThrownBy(() -> lifecycle.start("j-1", "1234"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Wrong OTP");
        assertThat(job.status()).isEqualTo(JobStatus.EN_ROUTE);
        assertThat(maidStatus()).isEqualTo(LiveStatus.EN_ROUTE);
    }

    @Test
    void cancellingIsBlockedOnceTheMaidIsOnHerWay() {
        liveStates.changeStatus("m-01", LiveStatus.IDLE);
        lifecycle.enRoute("j-1");

        TimeBasedCancellationPolicy policy = new TimeBasedCancellationPolicy(new CancellationProperties(null, null, null));
        assertThatThrownBy(() -> policy.evaluate(job, null, CancelledBy.CUSTOMER, MON.atTime(8, 5)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void aMaidWhoHasNotCheckedInCannotSetOff() {
        assertThatThrownBy(() -> lifecycle.enRoute("j-1")).isInstanceOf(InvalidStateTransitionException.class);

        assertThat(job.status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(maidStatus()).isEqualTo(LiveStatus.OFFLINE);
    }

    @Test
    void onlyAnAssignedJobCanSetOffAndTheMaidIsLeftAsSheWas() {
        liveStates.changeStatus("m-01", LiveStatus.IDLE);
        job.unassign();

        assertThatThrownBy(() -> lifecycle.enRoute("j-1"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(maidStatus()).isEqualTo(LiveStatus.IDLE);
    }

    @Test
    void aJobCannotBeCompletedBeforeItStarts() {
        liveStates.changeStatus("m-01", LiveStatus.IDLE);
        lifecycle.enRoute("j-1");

        assertThatThrownBy(() -> lifecycle.complete("j-1")).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(maidStatus()).isEqualTo(LiveStatus.EN_ROUTE);
    }
}
