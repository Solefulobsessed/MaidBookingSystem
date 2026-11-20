package com.example.maidbooking.supply;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class ShiftRepository extends InMemoryRepository<Shift, String> {

    private static final Comparator<Shift> BY_START = Comparator.comparing(shift -> shift.window().start());

    public ShiftRepository() {
        super("Shift", Shift::id);
    }

    public List<Shift> findByMaidAndDate(String maidId, LocalDate date) {
        return sorted(findAllMatching(s -> s.maidId().equals(maidId) && s.date().equals(date)));
    }

    public List<Shift> findByZoneAndDate(String zoneId, LocalDate date) {
        return sorted(findAllMatching(s -> s.zoneId().equals(zoneId) && s.date().equals(date)));
    }

    /** Shifts of this maid that still count towards capacity and overlap the window. */
    public List<Shift> findActiveOverlapping(String maidId, TimeSlot window) {
        return sorted(findAllMatching(s -> s.maidId().equals(maidId) && s.addsCapacity() && s.window().overlaps(window)));
    }

    private static List<Shift> sorted(List<Shift> shifts) {
        return shifts.stream().sorted(BY_START).toList();
    }
}
