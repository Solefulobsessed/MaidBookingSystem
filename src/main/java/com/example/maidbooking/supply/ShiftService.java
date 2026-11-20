package com.example.maidbooking.supply;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.common.lock.LockKey;
import com.example.maidbooking.common.lock.LockRegistry;
import com.example.maidbooking.zone.ZoneRepository;

@Service
public class ShiftService {

    static final int MAX_GENERATION_DAYS = 31;

    private final MaidRepository maids;
    private final ZoneRepository zones;
    private final ShiftRepository shifts;
    private final WeeklyShiftTemplateRepository templates;
    private final LockRegistry locks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ShiftService(MaidRepository maids, ZoneRepository zones, ShiftRepository shifts,
                        WeeklyShiftTemplateRepository templates, LockRegistry locks,
                        ApplicationEventPublisher events, Clock clock) {
        this.maids = maids;
        this.zones = zones;
        this.shifts = shifts;
        this.templates = templates;
        this.locks = locks;
        this.events = events;
        this.clock = clock;
    }

    public WeeklyShiftTemplate setTemplate(String maidId, List<ShiftTemplate> windows) {
        requireActiveMaid(maidId);
        windows.forEach(window -> zones.getById(window.zoneId()));
        return templates.save(new WeeklyShiftTemplate(maidId, windows));
    }

    /** Creates shifts for each template window in {@code [from, to]}; windows that clash with an existing shift are skipped. */
    public ShiftGenerationResult generateShifts(String maidId, LocalDate from, LocalDate to) {
        requireActiveMaid(maidId);
        WeeklyShiftTemplate template = templates.getById(maidId);
        validateRange(from, to);
        return locks.callWith(LockKey.maid(maidId), () -> {
            List<Shift> created = new ArrayList<>();
            int skipped = 0;
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                for (ShiftTemplate window : template.windows()) {
                    if (window.day() != date.getDayOfWeek()) {
                        continue;
                    }
                    TimeSlot slot = new TimeSlot(date.atTime(window.start()), date.atTime(window.end()));
                    if (!shifts.findActiveOverlapping(maidId, slot).isEmpty()) {
                        skipped++;
                        continue;
                    }
                    created.add(create(maidId, window.zoneId(), slot));
                }
            }
            return new ShiftGenerationResult(created, skipped);
        });
    }

    private Shift create(String maidId, String zoneId, TimeSlot slot) {
        Shift shift = shifts.save(new Shift("s-" + UUID.randomUUID().toString().substring(0, 8), maidId, zoneId, slot));
        events.publishEvent(ShiftChanged.added(shift));
        return shift;
    }

    private void requireActiveMaid(String maidId) {
        if (!maids.getById(maidId).isActive()) {
            throw new ValidationException("Maid " + maidId + " is not active");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("Generation end " + to + " is before start " + from);
        }
        if (from.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("Cannot generate shifts in the past (" + from + ")");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_GENERATION_DAYS) {
            throw new ValidationException("Generate at most " + MAX_GENERATION_DAYS + " days at a time");
        }
    }
}
