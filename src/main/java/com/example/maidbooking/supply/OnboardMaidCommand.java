package com.example.maidbooking.supply;

import java.util.Set;

/** Everything needed to onboard a maid; validated by {@link Maid} and {@link MaidService}. */
public record OnboardMaidCommand(String name, Gender gender, String phone, String homeZoneId,
                                 Set<ServiceType> skills, Set<String> tags, Tier tier) {
}
