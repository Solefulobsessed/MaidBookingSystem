package com.example.maidbooking.supply;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class MaidRepository extends InMemoryRepository<Maid, String> {

    public MaidRepository() {
        super("Maid", Maid::id);
    }

    public List<Maid> findActiveByHomeZone(String zoneId) {
        return findAllMatching(maid -> maid.isActive() && maid.homeZoneId().equals(zoneId));
    }
}
