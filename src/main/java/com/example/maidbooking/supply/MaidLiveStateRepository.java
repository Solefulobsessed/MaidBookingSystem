package com.example.maidbooking.supply;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class MaidLiveStateRepository extends InMemoryRepository<MaidLiveState, String> {

    public MaidLiveStateRepository() {
        super("Maid live state", MaidLiveState::maidId);
    }

    /** A maid with no record yet is OFFLINE. */
    public MaidLiveState getOrOffline(String maidId) {
        return findById(maidId).orElseGet(() -> new MaidLiveState(maidId));
    }

    public List<MaidLiveState> findByStatusInZone(LiveStatus status, String zoneId) {
        return findAllMatching(s -> s.status() == status && zoneId.equals(s.zoneId()));
    }
}
