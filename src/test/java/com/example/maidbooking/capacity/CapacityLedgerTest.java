package com.example.maidbooking.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.LeaveRepository;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftChanged;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.ShiftService;
import com.example.maidbooking.supply.ShiftTemplate;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.supply.WeeklyShiftTemplateRepository;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

class CapacityLedgerTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CapacityBucketRepository buckets = new CapacityBucketRepository();
    private final MaidRepository maids = new MaidRepository();
    private final InMemoryLockRegistry locks = new InMemoryLockRegistry();
    private final CapacityLedger ledger = new CapacityLedger(buckets, maids, locks);

    @BeforeEach
    void seedMaids() {
        maids.save(activeMaid("m-both", "9876543210", ServiceType.CLEANING, ServiceType.COOKING));
        maids.save(activeMaid("m-clean", "9876543211", ServiceType.CLEANING));
    }

    private static Maid activeMaid(String id, String phone, ServiceType... skills) {
        Maid maid = new Maid(id, "Maid " + id, Gender.FEMALE, phone, Set.of(skills), null, Tier.STANDARD, "kor-5", 0);
        maid.activate();
        return maid;
    }

    private static Shift shift(String id, String maidId, LocalTime from, LocalTime to) {
        return new Shift(id, maidId, "kor-5", new TimeSlot(MON.atTime(from), MON.atTime(to)));
    }

    private long capacity(String pool, int hour, int minute) {
        return buckets.findById(new BucketKey("kor-5", pool, MON.atTime(hour, minute)))
                .map(CapacityBucket::capacityMinutes).orElse(0L);
    }

    @Test
    void shiftAddsMinutesToEachSkillPoolAndOnceToTheZonePool() {
        ledger.onShiftChanged(ShiftChanged.added(shift("s-1", "m-both", LocalTime.of(7, 0), LocalTime.of(9, 0))));

        for (LocalDateTime start = MON.atTime(7, 0); start.isBefore(MON.atTime(9, 0)); start = start.plusMinutes(30)) {
            assertThat(capacity("CLEANING", start.getHour(), start.getMinute())).isEqualTo(30);
            assertThat(capacity("COOKING", start.getHour(), start.getMinute())).isEqualTo(30);
            assertThat(capacity("ANY", start.getHour(), start.getMinute())).isEqualTo(30);
        }
        assertThat(capacity("LAUNDRY", 7, 0)).isZero();
        assertThat(capacity("ANY", 9, 0)).isZero();
    }

    @Test
    void twoMaidsAddUpPerPool() {
        ledger.onShiftChanged(ShiftChanged.added(shift("s-1", "m-both", LocalTime.of(7, 0), LocalTime.of(9, 0))));
        ledger.onShiftChanged(ShiftChanged.added(shift("s-2", "m-clean", LocalTime.of(7, 0), LocalTime.of(9, 0))));

        assertThat(capacity("CLEANING", 8, 0)).isEqualTo(60);
        assertThat(capacity("COOKING", 8, 0)).isEqualTo(30);
        assertThat(capacity("ANY", 8, 0)).isEqualTo(60);
    }

    @Test
    void unalignedShiftFillsPartialBuckets() {
        ledger.onShiftChanged(ShiftChanged.added(shift("s-1", "m-clean", LocalTime.of(7, 15), LocalTime.of(9, 15))));

        assertThat(capacity("CLEANING", 7, 0)).isEqualTo(15);
        assertThat(capacity("CLEANING", 7, 30)).isEqualTo(30);
        assertThat(capacity("CLEANING", 9, 0)).isEqualTo(15);
    }

    @Test
    void removingAShiftTakesBackExactlyItsMinutes() {
        Shift morning = shift("s-1", "m-both", LocalTime.of(7, 0), LocalTime.of(9, 0));
        ledger.onShiftChanged(ShiftChanged.added(morning));
        ledger.onShiftChanged(ShiftChanged.added(shift("s-2", "m-clean", LocalTime.of(7, 0), LocalTime.of(9, 0))));

        ledger.onShiftChanged(ShiftChanged.removed(morning));

        assertThat(capacity("CLEANING", 8, 0)).isEqualTo(30);
        assertThat(capacity("COOKING", 8, 0)).isZero();
        assertThat(capacity("ANY", 8, 0)).isEqualTo(30);
    }

    @Test
    void shiftServiceAndLedgerTogetherRespectTheLockOrder() {
        ZoneRepository zones = new ZoneRepository();
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        Clock sundayBefore = Clock.fixed(MON.minusDays(1).atStartOfDay(IST).toInstant(), IST);
        ShiftService shiftService = new ShiftService(maids, zones, new ShiftRepository(),
                new WeeklyShiftTemplateRepository(), new LeaveRepository(), locks,
                event -> ledger.onShiftChanged((ShiftChanged) event), sundayBefore);
        shiftService.setTemplate("m-both",
                List.of(new ShiftTemplate(MON.getDayOfWeek(), "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0))));

        shiftService.generateShifts("m-both", MON, MON);
        assertThat(capacity("COOKING", 10, 0)).isEqualTo(30);

        shiftService.addLeave("m-both", MON, MON, "Fever");
        assertThat(capacity("COOKING", 10, 0)).isZero();
        assertThat(capacity("ANY", 10, 0)).isZero();
    }
}
