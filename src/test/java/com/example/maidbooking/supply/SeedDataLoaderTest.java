package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.discovery.SlotStatus;
import com.example.maidbooking.zone.ZoneRepository;

class SeedDataLoaderTest {

    @Nested
    @SpringBootTest
    class WithTheDefaultDemoProfile {

        @Autowired
        private ZoneRepository zones;

        @Autowired
        private MaidRepository maids;

        @Autowired
        private ShiftRepository shifts;

        @Autowired
        private SlotService slots;

        @Autowired
        private Clock clock;

        @Test
        void threeZonesAndTwelveActiveMaidsAreLoaded() {
            assertThat(zones.findAll()).extracting(zone -> zone.id()).containsExactlyInAnyOrder("kor-5", "hsr-2", "ind-1");
            assertThat(maids.findAll()).hasSize(12).allMatch(Maid::isActive);
            assertThat(maids.findAll()).extracting(Maid::homeZoneId).containsOnly("kor-5", "hsr-2", "ind-1");
        }

        @Test
        void everyMaidHasShiftsOnEveryWorkingDayOfTheComingWeek() {
            LocalDate today = LocalDate.now(clock);
            for (LocalDate day = today; day.isBefore(today.plusDays(7)); day = day.plusDays(1)) {
                LocalDate date = day;
                long expected = date.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : 12;
                assertThat(maids.findAll().stream().filter(m -> !shifts.findByMaidAndDate(m.id(), date).isEmpty()))
                        .as(date.toString()).hasSize((int) expected);
            }
        }

        @Test
        void theLedgerHasOpenSlotsAfterStartup() {
            LocalDate day = LocalDate.now(clock).plusDays(2);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                day = day.plusDays(1);
            }

            assertThat(slots.slots("koramangala", ServiceType.CLEANING, day, 60, Tier.STANDARD).slots())
                    .anyMatch(slot -> slot.status() == SlotStatus.OPEN);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class WithAnotherProfile {

        @Autowired
        private ZoneRepository zones;

        @Autowired
        private MaidRepository maids;

        @Test
        void nothingIsSeeded() {
            assertThat(zones.findAll()).isEmpty();
            assertThat(maids.findAll()).isEmpty();
        }
    }
}
