package com.example.maidbooking.supply;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.NotFoundException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.InMemoryLockRegistry;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

class ShiftServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final Clock SUNDAY_BEFORE = Clock.fixed(
            MON.minusDays(1).atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));

    private final MaidRepository maids = new MaidRepository();
    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final List<Object> published = new ArrayList<>();
    private final ShiftService service = new ShiftService(maids, zones, shifts, new WeeklyShiftTemplateRepository(),
            new InMemoryLockRegistry(), published::add, SUNDAY_BEFORE);

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
        Maid maid = MaidTest.maid("m-1", Set.of(ServiceType.CLEANING));
        maid.activate();
        maids.save(maid);
    }

    private static List<ShiftTemplate> weekdayMornings() {
        List<ShiftTemplate> windows = new ArrayList<>();
        for (DayOfWeek day : EnumSet.range(MONDAY, DayOfWeek.FRIDAY)) {
            windows.add(new ShiftTemplate(day, "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0)));
        }
        return windows;
    }

    @Test
    void generatesOneShiftPerTemplateWindowAndAnnouncesEach() {
        service.setTemplate("m-1", weekdayMornings());

        ShiftGenerationResult result = service.generateShifts("m-1", MON, MON.plusDays(13));

        assertThat(result.created()).hasSize(10);
        assertThat(result.skippedOverlapping()).isZero();
        assertThat(result.created().get(0).window()).isEqualTo(new TimeSlot(MON.atTime(7, 0), MON.atTime(13, 0)));
        assertThat(shifts.findByMaidAndDate("m-1", MON.plusDays(5))).isEmpty();
        assertThat(published).hasSize(10).allSatisfy(event ->
                assertThat(((ShiftChanged) event).change()).isEqualTo(ShiftChanged.Change.CAPACITY_ADDED));
    }

    @Test
    void runningGenerationTwiceCreatesNoDuplicates() {
        service.setTemplate("m-1", weekdayMornings());
        service.generateShifts("m-1", MON, MON.plusDays(6));

        ShiftGenerationResult again = service.generateShifts("m-1", MON, MON.plusDays(6));

        assertThat(again.created()).isEmpty();
        assertThat(again.skippedOverlapping()).isEqualTo(5);
        assertThat(shifts.findAll()).hasSize(5);
    }

    @Test
    void windowClashingWithAnExistingShiftIsSkipped() {
        shifts.save(new Shift("s-manual", "m-1", "kor-5", new TimeSlot(MON.atTime(11, 0), MON.atTime(15, 0))));
        service.setTemplate("m-1", List.of(new ShiftTemplate(MONDAY, "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0)),
                new ShiftTemplate(TUESDAY, "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0))));

        ShiftGenerationResult result = service.generateShifts("m-1", MON, MON.plusDays(1));

        assertThat(result.created()).extracting(Shift::date).containsExactly(MON.plusDays(1));
        assertThat(result.skippedOverlapping()).isEqualTo(1);
    }

    @Test
    void templateRules() {
        assertThatThrownBy(() -> service.setTemplate("m-1",
                List.of(new ShiftTemplate(MONDAY, "nowhere", LocalTime.of(7, 0), LocalTime.of(13, 0)))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setTemplate("m-1",
                List.of(new ShiftTemplate(MONDAY, "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0)),
                        new ShiftTemplate(MONDAY, "kor-5", LocalTime.of(12, 0), LocalTime.of(16, 0)))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("overlap");
        assertThatThrownBy(() -> new ShiftTemplate(MONDAY, "kor-5", LocalTime.of(7, 0), LocalTime.of(8, 0)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void inactiveMaidCannotGetATemplate() {
        maids.getById("m-1").suspend();

        assertThatThrownBy(() -> service.setTemplate("m-1", weekdayMornings()))
                .isInstanceOf(ValidationException.class).hasMessageContaining("not active");
    }

    @Test
    void generationNeedsATemplateAndASensibleRange() {
        assertThatThrownBy(() -> service.generateShifts("m-1", MON, MON.plusDays(6)))
                .isInstanceOf(NotFoundException.class);
        service.setTemplate("m-1", weekdayMornings());

        assertThatThrownBy(() -> service.generateShifts("m-1", MON.minusDays(2), MON))
                .isInstanceOf(ValidationException.class).hasMessageContaining("past");
        assertThatThrownBy(() -> service.generateShifts("m-1", MON, MON.minusDays(1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.generateShifts("m-1", MON, MON.plusDays(31)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("31 days");
        assertThat(service.generateShifts("m-1", MON, MON.plusDays(30)).created()).isNotEmpty();
    }
}
