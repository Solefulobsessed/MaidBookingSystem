package com.example.maidbooking.allocation.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class MaidScorerTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final Duration TRAVEL = Duration.ofMinutes(15);
    private static final TimeSlot JOB = slot("10:00", "11:00");

    private static TimeSlot slot(String from, String to) {
        return new TimeSlot(MON.atTime(LocalTime.parse(from)), MON.atTime(LocalTime.parse(to)));
    }

    private static Candidate candidate(String maidId, Assignment... existing) {
        Maid maid = new Maid(maidId, "Maid " + maidId, Gender.FEMALE, "98765432" + maidId.substring(2),
                Set.of(ServiceType.CLEANING), null, Tier.STANDARD, "kor-5", 4.5);
        Shift shift = new Shift("s-" + maidId, maidId, "kor-5", slot("07:00", "13:00"));
        return new Candidate(maid, shift, new MaidCalendar(maidId, List.of(existing)), JOB.withBufferBefore(TRAVEL));
    }

    private static Assignment job(String maidId, String from, String to) {
        TimeSlot work = slot(from, to);
        return new Assignment("a-" + from, "j-" + from, maidId, "s-" + maidId, "kor-5", work,
                work.withBufferBefore(TRAVEL), AssignmentStatus.ACTIVE);
    }

    private static AllocationRequest request(String preferredMaidId) {
        return new AllocationRequest("j-new", "kor-5", ServiceType.CLEANING, JOB, MaidFilter.all(List.of()),
                preferredMaidId, null);
    }

    private final MaidScorer scorer = new MaidScorer(List.of(new BestFitGapRule(), new PreferredMaidRule()),
            new ScoringProperties(Map.of("best-fit-gap", 1.0, "preferred-maid", 10.0)));

    // A: busy 08:30-09:30 and 11:15-12:15 -> the 10:00 job leaves only 15 min idle. B: free all day.
    private final Candidate tightFit = candidate("m-01", job("m-01", "08:30", "09:30"), job("m-01", "11:15", "12:15"));
    private final Candidate emptyDay = candidate("m-02");

    @Test
    void bestFitPrefersTheTightestGap() {
        BestFitGapRule rule = new BestFitGapRule();

        assertThat(rule.score(tightFit, request(null))).isCloseTo(1 / 1.5, within(1e-9));
        assertThat(rule.score(emptyDay, request(null))).isCloseTo(1 / (1 + 285 / 30.0), within(1e-9));
        assertThat(scorer.rank(List.of(emptyDay, tightFit), request(null)))
                .extracting(s -> s.candidate().maid().id()).containsExactly("m-01", "m-02");
    }

    @Test
    void perfectFitScoresOne() {
        Candidate perfect = candidate("m-03", job("m-03", "07:00", "09:45"), job("m-03", "11:15", "13:00"));

        assertThat(new BestFitGapRule().score(perfect, request(null))).isEqualTo(1.0);
    }

    @Test
    void preferredMaidOutweighsBestFit() {
        List<ScoredCandidate> ranked = scorer.rank(List.of(tightFit, emptyDay), request("m-02"));

        assertThat(ranked.get(0).candidate().maid().id()).isEqualTo("m-02");
        assertThat(ranked.get(0).reasons()).containsEntry("preferred-maid", 10.0).containsKey("best-fit-gap");
        assertThat(ranked.get(0).total()).isGreaterThan(10.0);
    }

    @Test
    void tiesGoToTheLowerMaidIdSoResultsAreRepeatable() {
        Candidate twinB = candidate("m-05");
        Candidate twinA = candidate("m-04");

        assertThat(scorer.rank(List.of(twinB, twinA), request(null)))
                .extracting(s -> s.candidate().maid().id()).containsExactly("m-04", "m-05");
    }

    @Test
    void weightsDefaultToOneAndCannotBeNegative() {
        assertThat(new ScoringProperties(null).weightOf("anything")).isEqualTo(1.0);
        assertThatThrownBy(() -> new ScoringProperties(Map.of("best-fit-gap", -1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestCopiesExcludedMaids() {
        assertThat(request(null).excludedMaidIds()).isEmpty();
        assertThatThrownBy(() -> new AllocationRequest(null, "kor-5", ServiceType.CLEANING, JOB,
                MaidFilter.all(List.of()), null, null)).isInstanceOf(NullPointerException.class);
    }
}
