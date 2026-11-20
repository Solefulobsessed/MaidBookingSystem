package com.example.maidbooking.supply;

import static com.example.maidbooking.supply.ShiftTest.MON;
import static com.example.maidbooking.supply.ShiftTest.shift;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;

class ShiftRepositoryTest {

    private final ShiftRepository repo = new ShiftRepository();

    @BeforeEach
    void seed() {
        repo.save(shift("s-pm", "m-1", "kor-5", 14, 18));
        repo.save(shift("s-am", "m-1", "kor-5", 7, 12));
        repo.save(shift("s-other-maid", "m-2", "kor-5", 7, 12));
        repo.save(shift("s-other-zone", "m-3", "hsr-1", 7, 12));
        repo.save(new Shift("s-tue", "m-1", "kor-5", new TimeSlot(MON.plusDays(1).atTime(7, 0), MON.plusDays(1).atTime(12, 0))));
    }

    @Test
    void byMaidAndDateIsSortedByStart() {
        assertThat(repo.findByMaidAndDate("m-1", MON)).extracting(Shift::id).containsExactly("s-am", "s-pm");
    }

    @Test
    void byZoneAndDate() {
        assertThat(repo.findByZoneAndDate("kor-5", MON)).extracting(Shift::id)
                .containsExactly("s-am", "s-other-maid", "s-pm");
    }

    @Test
    void activeOverlappingIgnoresCancelledShifts() {
        TimeSlot morning = new TimeSlot(MON.atTime(11, 0), MON.atTime(15, 0));

        assertThat(repo.findActiveOverlapping("m-1", morning)).extracting(Shift::id).containsExactly("s-am", "s-pm");
        repo.getById("s-pm").cancel();
        assertThat(repo.findActiveOverlapping("m-1", morning)).extracting(Shift::id).containsExactly("s-am");
    }
}
