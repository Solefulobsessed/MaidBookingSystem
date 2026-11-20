package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

class MaidTest {

    static Maid maid(String id, Set<ServiceType> skills) {
        return new Maid(id, "Lakshmi R", Gender.FEMALE, "9876543210", skills,
                Set.of(" South-Indian-Cooking "), Tier.PREMIUM, "kor-5", 4.6);
    }

    @Test
    void newMaidStartsOnboarding() {
        Maid maid = maid("m-1", Set.of(ServiceType.CLEANING));

        assertThat(maid.status()).isEqualTo(MaidStatus.ONBOARDING);
        assertThat(maid.isActive()).isFalse();
    }

    @Test
    void activateThenSuspendThenActivateAgain() {
        Maid maid = maid("m-1", Set.of(ServiceType.CLEANING));

        maid.activate();
        assertThat(maid.isActive()).isTrue();
        maid.suspend();
        assertThat(maid.status()).isEqualTo(MaidStatus.SUSPENDED);
        maid.activate();
        assertThat(maid.isActive()).isTrue();
    }

    @Test
    void illegalTransitionsThrow() {
        Maid maid = maid("m-1", Set.of(ServiceType.CLEANING));

        assertThatThrownBy(maid::suspend).isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage("Cannot suspend Maid in state ONBOARDING");
        maid.activate();
        assertThatThrownBy(maid::activate).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void skillsAndTags() {
        Maid maid = maid("m-1", Set.of(ServiceType.CLEANING, ServiceType.COOKING));

        assertThat(maid.offers(ServiceType.COOKING)).isTrue();
        assertThat(maid.offers(ServiceType.BABYSITTING)).isFalse();
        assertThat(maid.tags()).containsExactly("south-indian-cooking");
        assertThat(maid.hasTag("SOUTH-INDIAN-COOKING")).isTrue();
    }

    @Test
    void skillsAreCopied() {
        Set<ServiceType> skills = new HashSet<>(Set.of(ServiceType.CLEANING));
        Maid maid = maid("m-1", skills);
        skills.add(ServiceType.COOKING);

        assertThat(maid.skills()).containsExactly(ServiceType.CLEANING);
    }

    @Test
    void zeroRatingMeansUnrated() {
        Maid fresh = new Maid("m-2", "Asha", Gender.FEMALE, "+919876543210", Set.of(ServiceType.LAUNDRY),
                null, Tier.STANDARD, "kor-5", 0.0);

        assertThat(fresh.isRated()).isFalse();
        assertThat(fresh.tags()).isEmpty();
    }

    @Test
    void rejectsInvalidProfiles() {
        assertThatThrownBy(() -> maid("m-1", Set.of())).isInstanceOf(ValidationException.class)
                .hasMessageContaining("skill");
        assertThatThrownBy(() -> new Maid("m-1", " ", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING),
                null, Tier.STANDARD, "kor-5", 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Maid("m-1", "Asha", Gender.FEMALE, "12345", Set.of(ServiceType.CLEANING),
                null, Tier.STANDARD, "kor-5", 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Maid("m-1", "Asha", Gender.FEMALE, "9876543210", Set.of(ServiceType.CLEANING),
                null, Tier.STANDARD, "kor-5", 5.1)).isInstanceOf(ValidationException.class);
    }
}
