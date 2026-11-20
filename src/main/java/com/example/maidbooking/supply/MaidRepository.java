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

    /** Matches the last 10 digits, so "+919876543210" and "9876543210" are the same number. */
    public boolean existsWithPhone(String phone) {
        String digits = lastTenDigits(phone);
        return !findAllMatching(maid -> lastTenDigits(maid.phone()).equals(digits)).isEmpty();
    }

    private static String lastTenDigits(String phone) {
        return phone.substring(phone.length() - 10);
    }
}
