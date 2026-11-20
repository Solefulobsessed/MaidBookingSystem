package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class MaidRepositoryTest {

    private final MaidRepository repo = new MaidRepository();

    @Test
    void findsOnlyActiveMaidsOfAZone() {
        Maid active = MaidTest.maid("m-1", Set.of(ServiceType.CLEANING));
        active.activate();
        Maid onboarding = MaidTest.maid("m-2", Set.of(ServiceType.CLEANING));
        Maid suspended = MaidTest.maid("m-3", Set.of(ServiceType.CLEANING));
        suspended.activate();
        suspended.suspend();
        Maid otherZone = new Maid("m-4", "Asha", Gender.FEMALE, "9876543211", Set.of(ServiceType.CLEANING),
                null, Tier.STANDARD, "hsr-1", 4.0);
        otherZone.activate();
        repo.save(active);
        repo.save(onboarding);
        repo.save(suspended);
        repo.save(otherZone);

        assertThat(repo.findActiveByHomeZone("kor-5")).extracting(Maid::id).containsExactly("m-1");
        assertThat(repo.getById("m-2").status()).isEqualTo(MaidStatus.ONBOARDING);
    }
}
