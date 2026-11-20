package com.example.maidbooking.allocation.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.allocation.AllocationRequest;
import com.example.maidbooking.allocation.Assignment;
import com.example.maidbooking.allocation.AssignmentStatus;
import com.example.maidbooking.allocation.Candidate;
import com.example.maidbooking.allocation.MaidCalendar;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.Tier;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneDistanceTravelTimeEstimator;
import com.example.maidbooking.zone.ZoneRepository;

class ScoringRulesTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final TimeSlot JOB = slot(MON, "11:00", "12:00");
    private static final AllocationRequest REQUEST = new AllocationRequest("j-new", "kor-5", ServiceType.CLEANING,
            JOB, MaidFilter.all(List.of()), null, null);

    private final ZoneRepository zones = new ZoneRepository();
    private final TravelDistanceRule travel;

    ScoringRulesTest() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of("hsr-1")));
        zones.save(new Zone("hsr-1", "HSR Layout", Set.of("hsr layout"), Set.of()));
        zones.save(new Zone("wf-1", "Whitefield", Set.of("whitefield"), Set.of()));
        travel = new TravelDistanceRule(zones, new ZoneDistanceTravelTimeEstimator(15, 30));
    }

    private static TimeSlot slot(LocalDate day, String from, String to) {
        return new TimeSlot(day.atTime(LocalTime.parse(from)), day.atTime(LocalTime.parse(to)));
    }

    private static Assignment job(String maidId, String zoneId, LocalDate day, String from, String to) {
        TimeSlot work = slot(day, from, to);
        return new Assignment("a-" + maidId + from + day, "j", maidId, "s", zoneId, work,
                work.withBufferBefore(Duration.ofMinutes(15)), AssignmentStatus.ACTIVE);
    }

    private static Candidate candidate(String maidId, double rating, String shiftZone, Assignment... jobs) {
        Maid maid = new Maid(maidId, "Maid", Gender.FEMALE, "98765432" + maidId.substring(2),
                Set.of(ServiceType.CLEANING), null, Tier.STANDARD, shiftZone, rating);
        Shift shift = new Shift("s-" + maidId, maidId, shiftZone, slot(MON, "07:00", "17:00"));
        return new Candidate(maid, shift, new MaidCalendar(maidId, List.of(jobs)),
                JOB.withBufferBefore(Duration.ofMinutes(15)));
    }

    @Test
    void travelFromHerShiftZoneWhenItIsHerFirstJob() {
        assertThat(travel.score(candidate("m-01", 4, "kor-5"), REQUEST)).isEqualTo(0.75);
        assertThat(travel.score(candidate("m-02", 4, "hsr-1"), REQUEST)).isEqualTo(0.5);
        assertThat(travel.score(candidate("m-03", 4, "wf-1"), REQUEST)).as("out of reach").isZero();
    }

    @Test
    void travelFromWhereHerPreviousJobWas() {
        Candidate cameFromHsr = candidate("m-04", 4, "kor-5", job("m-04", "hsr-1", MON, "08:00", "10:00"));

        assertThat(travel.score(cameFromHsr, REQUEST)).isEqualTo(0.5);
    }

    @Test
    void fairnessFavoursMaidsWithFewerJobsThatDay() {
        FairnessRule fairness = new FairnessRule();

        assertThat(fairness.score(candidate("m-05", 4, "kor-5"), REQUEST)).isEqualTo(1.0);
        assertThat(fairness.score(candidate("m-06", 4, "kor-5", job("m-06", "kor-5", MON, "08:00", "09:00")),
                REQUEST)).isEqualTo(0.5);
        assertThat(fairness.score(candidate("m-07", 4, "kor-5", job("m-07", "kor-5", MON.plusDays(1), "08:00", "09:00")),
                REQUEST)).as("tomorrow's jobs don't count").isEqualTo(1.0);
    }

    @Test
    void ratingIsOutOfFiveAndUnratedIsNeutral() {
        RatingRule rating = new RatingRule();

        assertThat(rating.score(candidate("m-08", 4.5, "kor-5"), REQUEST)).isCloseTo(0.9, within(1e-9));
        assertThat(rating.score(candidate("m-09", 0, "kor-5"), REQUEST)).isEqualTo(RatingRule.UNRATED);
    }

    @Test
    void allRulesTogetherGiveTheIdleNearbyMaidTheJobWhenFitsAreEqual() {
        MaidScorer scorer = new MaidScorer(
                List.of(new BestFitGapRule(), new PreferredMaidRule(), travel, new FairnessRule(), new RatingRule()),
                new ScoringProperties(Map.of("best-fit-gap", 1.0, "preferred-maid", 10.0,
                        "travel-distance", 1.0, "fairness", 0.5, "rating", 0.5)));
        Candidate busyHere = candidate("m-10", 4.5, "kor-5", job("m-10", "kor-5", MON, "07:00", "08:00"));
        Candidate idleHere = candidate("m-11", 4.5, "kor-5");
        Candidate idleNextDoor = candidate("m-12", 4.5, "hsr-1");

        List<ScoredCandidate> ranked = scorer.rank(List.of(idleNextDoor, busyHere, idleHere), REQUEST);

        assertThat(ranked).extracting(s -> s.candidate().maid().id()).containsExactly("m-11", "m-10", "m-12");
        assertThat(ranked.get(0).reasons()).containsOnlyKeys("best-fit-gap", "preferred-maid", "travel-distance",
                "fairness", "rating");
    }
}
