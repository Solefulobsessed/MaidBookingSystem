package com.example.maidbooking.api.maid;

import java.util.Set;

import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidStatus;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

public record MaidResponse(String id, String name, Gender gender, String phone, String homeZone,
                           Set<ServiceType> skills, Set<String> tags, Tier tier, double rating, MaidStatus status) {

    static MaidResponse from(Maid maid) {
        return new MaidResponse(maid.id(), maid.name(), maid.gender(), maid.phone(), maid.homeZoneId(),
                maid.skills(), maid.tags(), maid.tier(), maid.rating(), maid.status());
    }
}
