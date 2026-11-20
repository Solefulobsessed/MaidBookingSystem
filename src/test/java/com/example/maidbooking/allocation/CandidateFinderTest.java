package com.example.maidbooking.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidRepository;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class CandidateFinderTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot JOB = slot("11:00", "12:00");
    private static final MaidFilter ANYONE = MaidFilter.all(List.of());
    private static final MaidFilter FEMALE_ONLY = new MaidFilter() {
        @Override
        public boolean matches(Maid maid) {
            return maid.gender() == Gender.FEMALE;
        }

        @Override
        public String describe() {
            return "gender=FEMALE";
        }
    };

    private final ZoneRepository zones = new ZoneRepository();
    private final ShiftRepository shifts = new ShiftRepository();
    private final MaidRepository maids = new MaidRepository();
    private final AssignmentRepository assignments = new AssignmentRepository();
    private final CandidateFinder finder = new CandidateFinder(zones, shifts, maids, assignments,
            new ZoneDistanceTravelTimeEstimator(15, 30));

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
    }

    private Maid maid(String id, String zone, Gender gender, ServiceType... skills) {
        Maid maid = new Maid(id, "Maid " + id, gender, "98765432" + id.substring(2), Set.of(skills), null,
                Tier.STANDARD, zone, 4.5);
        maid.activate();
        maids.save(maid);
        return maid;
    }

    private Shift shift(String maidId, String zone, String from, String to) {
        return shifts.save(new Shift("s-" + maidId, maidId, zone, slot(from, to)));
    }

    private void busy(String maidId, String zone, String from, String to) {
        TimeSlot work = slot(from, to);
        assignments.save(new Assignment("a-" + maidId + from, "j-" + from, maidId, "s-" + maidId, zone, work,
                work.withBufferBefore(Duration.ofMinutes(15)), AssignmentStatus.ACTIVE));
    }

    private List<String> candidateIds(MaidFilter constraints, String... excluded) {
        AllocationRequest request = new AllocationRequest("j-new", "kor-5", ServiceType.CLEANING, JOB, constraints,
                null, Set.of(excluded));
        return finder.find(request).stream().map(c -> c.maid().id()).toList();
    }

    @BeforeEach
    void seed() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of("hsr-1")));
        zones.save(new Zone("hsr-1", "HSR Layout", Set.of("hsr layout"), Set.of()));
        zones.save(new Zone("wf-1", "Whitefield", Set.of("whitefield"), Set.of()));
        maid("m-01", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-01", "kor-5", "07:00", "13:00");
        maid("m-02", "hsr-1", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-02", "hsr-1", "07:00", "13:00");
    }

    @Test
    void maidsInTheZoneAndNextDoorAreCandidatesWithTheirOwnTravel() {
        List<Candidate> found = finder.find(new AllocationRequest("j-new", "kor-5", ServiceType.CLEANING, JOB, ANYONE,
                null, null));

        assertThat(found).extracting(c -> c.maid().id()).containsExactly("m-01", "m-02");
        assertThat(found.get(0).blocked()).isEqualTo(slot("10:45", "12:00"));
        assertThat(found.get(1).blocked()).as("30 min from the neighbouring zone").isEqualTo(slot("10:30", "12:00"));
    }

    @Test
    void wrongZoneSkillOrStatusRulesAMaidOut() {
        maid("m-03", "wf-1", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-03", "wf-1", "07:00", "13:00");
        maid("m-04", "kor-5", Gender.FEMALE, ServiceType.COOKING);
        shift("m-04", "kor-5", "07:00", "13:00");
        maid("m-05", "kor-5", Gender.FEMALE, ServiceType.CLEANING).suspend();
        shift("m-05", "kor-5", "07:00", "13:00");
        maid("m-06", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-06", "kor-5", "07:00", "13:00").cancel();

        assertThat(candidateIds(ANYONE)).containsExactly("m-01", "m-02");
    }

    @Test
    void customerConstraintsAndExclusionsApply() {
        maid("m-07", "kor-5", Gender.MALE, ServiceType.CLEANING);
        shift("m-07", "kor-5", "07:00", "13:00");

        assertThat(candidateIds(ANYONE)).contains("m-07");
        assertThat(candidateIds(FEMALE_ONLY)).doesNotContain("m-07");
        assertThat(candidateIds(ANYONE, "m-01")).doesNotContain("m-01");
    }

    @Test
    void shiftMustCoverTheTravelAndTheJob() {
        maid("m-08", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-08", "kor-5", "11:00", "15:00");
        maid("m-09", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-09", "kor-5", "08:00", "11:45");

        assertThat(candidateIds(ANYONE)).doesNotContain("m-08", "m-09");
    }

    @Test
    void herCalendarMustHaveRoomIncludingTravelOnToHerNextJob() {
        maid("m-10", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-10", "kor-5", "07:00", "16:00");
        busy("m-10", "kor-5", "10:30", "11:30");
        maid("m-11", "kor-5", Gender.FEMALE, ServiceType.CLEANING);
        shift("m-11", "kor-5", "07:00", "16:00");
        busy("m-11", "hsr-1", "12:15", "13:00");

        assertThat(candidateIds(ANYONE)).as("m-10 overlaps; m-11 has 15 min for a 30 min trip")
                .doesNotContain("m-10", "m-11");
    }

    @Test
    void travelIsMeasuredFromHerPreviousJob() {
        busy("m-01", "hsr-1", "09:00", "10:00");

        Candidate lakshmi = finder.find(new AllocationRequest("j-new", "kor-5", ServiceType.CLEANING, JOB, ANYONE,
                null, null)).get(0);

        assertThat(lakshmi.blocked()).as("coming from HSR").isEqualTo(slot("10:30", "12:00"));
    }
}
