package com.example.maidbooking.api.maid;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.OnboardMaidCommand;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

public record OnboardMaidRequest(@NotBlank String name, @NotNull Gender gender, @NotBlank String phone,
                                 @NotBlank String homeZone, @NotEmpty Set<ServiceType> skills,
                                 Set<String> tags, @NotNull Tier tier) {

    OnboardMaidCommand toCommand() {
        return new OnboardMaidCommand(name, gender, phone, homeZone, skills, tags, tier);
    }
}
