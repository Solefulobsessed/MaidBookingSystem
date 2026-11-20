package com.example.maidbooking.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.MutableClock;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.supply.ServiceType;

class CapacityServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final Duration TRAVEL = Duration.ofMinutes(15);

    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final MutableClock clock = new MutableClock(MON.atTime(8, 0));
    private final List<Object> published = new ArrayList<>();

    private CapacityService service(double factor) {
        return new CapacityService(buckets, new CapacityReservationRepository(), new InMemoryLockRegistry(),
                published::add, clock, new CapacityProperties(factor));
    }

    /** Adds {@code minutes} per 30-min bucket between the hours, in the given service pool and the zone pool. */
    private void capacity(ServiceType service, int fromHour, int toHour, long minutes) {
        for (LocalDateTime t = MON.atTime(fromHour, 0); t.isBefore(MON.atTime(toHour, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", service, t)).addCapacity(minutes);
            buckets.getOrCreate(BucketKey.forZone("kor-5", t)).addCapacity(minutes);
        }
    }

    private long reserved(String pool, int hour, int minute) {
        return buckets.getOrCreate(new BucketKey("kor-5", pool, MON.atTime(hour, minute))).reservedMinutes();
    }

    private static TimeSlot jobAt(int hour, int minutes) {
        return TimeSlot.of(MON.atTime(hour, 0), Duration.ofMinutes(minutes)).withBufferBefore(TRAVEL);
    }

    @Test
    void reserveTakesMinutesFromServiceAndZonePoolsIncludingTravel() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityService capacityService = service(1.0);

        CapacityReservation reservation = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1",
                MON.atTime(8, 10));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(reservation.minutesPerBucket()).containsEntry(MON.atTime(8, 30), 15L).containsEntry(MON.atTime(9, 0), 30L);
        for (String pool : List.of("CLEANING", "ANY")) {
            assertThat(reserved(pool, 8, 30)).isEqualTo(15);
            assertThat(reserved(pool, 9, 0)).isEqualTo(30);
            assertThat(reserved(pool, 9, 30)).isEqualTo(30);
        }
    }

    @Test
    void fullBucketMeansNothingIsReservedAndConflictsAreListed() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityService capacityService = service(1.0);
        capacityService.reserve("kor-5", ServiceType.CLEANING, TimeSlot.of(MON.atTime(10, 0), Duration.ofMinutes(30)),
                "j-busy", null);

        assertThatThrownBy(() -> capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 90), "j-2", null))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
                    assertThat(e.details().get("conflicts")).isEqualTo(List.of(MON.atTime(10, 0).toString()));
                });
        assertThat(reserved("CLEANING", 8, 30)).as("all or nothing").isZero();
        assertThat(reserved("CLEANING", 9, 0)).isZero();
    }

    @Test
    void zonePoolStopsAMultiSkillMaidBeingSoldTwice() {
        // One maid who cleans and cooks: 30 min in each service pool, but only 30 in the zone pool.
        capacity(ServiceType.CLEANING, 9, 10, 30);
        for (LocalDateTime t = MON.atTime(9, 0); t.isBefore(MON.atTime(10, 0)); t = t.plusMinutes(30)) {
            buckets.getOrCreate(BucketKey.forService("kor-5", ServiceType.COOKING, t)).addCapacity(30);
        }
        CapacityService capacityService = service(1.0);
        TimeSlot nineToTen = TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1));
        capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-clean", null);

        assertThatThrownBy(() -> capacityService.reserve("kor-5", ServiceType.COOKING, nineToTen, "j-cook", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void noShiftsMeansNoCapacity() {
        assertThatThrownBy(() -> service(1.0).reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void utilisationFactorLimitsWhatCanBeSold() {
        capacity(ServiceType.CLEANING, 9, 10, 120); // 4 maids; 85% = 102 sellable per bucket
        CapacityService capacityService = service(0.85);
        TimeSlot nineToTen = TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1));
        for (int i = 0; i < 3; i++) {
            capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-" + i, null);
        }

        assertThatThrownBy(() -> capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-4", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void releaseGivesBackExactlyWhatWasTakenAndOnlyOnce() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityService capacityService = service(1.0);
        CapacityReservation reservation = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1", null);

        capacityService.release(reservation.id());

        assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reserved("CLEANING", 8, 30)).isZero();
        assertThat(reserved("ANY", 9, 30)).isZero();
        assertThatThrownBy(() -> capacityService.release(reservation.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-2", null)).isNotNull();
    }

    @Test
    void commitStopsTheHoldFromExpiring() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityService capacityService = service(1.0);
        LocalDateTime holdUntil = MON.atTime(8, 10);
        CapacityReservation reservation = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1", holdUntil);

        assertThat(reservation.isExpired(holdUntil.minusSeconds(1))).isFalse();
        assertThat(reservation.isExpired(holdUntil)).isTrue();
        capacityService.commit(reservation.id());

        assertThat(reservation.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(reservation.expiresAt()).isNull();
        assertThat(reservation.isExpired(holdUntil.plusHours(5))).isFalse();
        assertThatThrownBy(() -> capacityService.commit(reservation.id()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void holdWithoutExpiryNeverLapses() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityReservation reservation = service(1.0).reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1", null);

        assertThat(reservation.isExpired(MON.plusDays(30).atStartOfDay())).isFalse();
    }

    @Test
    void lapsedHoldIsReleasedLazilyWhenSomeoneElseBooks() {
        capacity(ServiceType.CLEANING, 9, 10, 30);
        CapacityService capacityService = service(1.0);
        TimeSlot nineToTen = TimeSlot.of(MON.atTime(9, 0), Duration.ofHours(1));
        CapacityReservation unpaid = capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-unpaid",
                MON.atTime(8, 10));
        assertThatThrownBy(() -> capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-2", null))
                .as("slot is held").isInstanceOf(ConflictException.class);

        clock.set(MON.atTime(8, 10));
        CapacityReservation second = capacityService.reserve("kor-5", ServiceType.CLEANING, nineToTen, "j-2", null);

        assertThat(unpaid.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(second.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(published).containsExactly(new CapacityHoldExpired(unpaid.id(), "j-unpaid", "kor-5"));
    }

    @Test
    void sweeperReleasesLapsedHoldsInEveryZone() {
        capacity(ServiceType.CLEANING, 7, 13, 60);
        CapacityService capacityService = service(1.0);
        CapacityReservation lapses = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1",
                MON.atTime(8, 10));
        CapacityReservation stillHeld = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(11, 60), "j-2",
                MON.atTime(8, 30));
        CapacityReservation paid = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(10, 30), "j-3",
                MON.atTime(8, 10));
        capacityService.commit(paid.id());

        clock.set(MON.atTime(8, 20));
        int released = capacityService.releaseExpiredHolds();

        assertThat(released).isEqualTo(1);
        assertThat(lapses.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(stillHeld.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(paid.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(reserved("CLEANING", 9, 0)).isZero();
        assertThat(published).hasSize(1);
    }

    @Test
    void payingAfterTheHoldLapsedIsRejectedAndFreesTheSlot() {
        capacity(ServiceType.CLEANING, 7, 13, 30);
        CapacityService capacityService = service(1.0);
        CapacityReservation reservation = capacityService.reserve("kor-5", ServiceType.CLEANING, jobAt(9, 60), "j-1",
                MON.atTime(8, 10));

        clock.set(MON.atTime(8, 11));

        assertThatThrownBy(() -> capacityService.commit(reservation.id()))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLD_EXPIRED));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reserved("CLEANING", 9, 0)).isZero();
        assertThat(published).hasSize(1);
    }
}
