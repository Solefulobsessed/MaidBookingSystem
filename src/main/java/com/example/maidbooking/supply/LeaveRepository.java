package com.example.maidbooking.supply;

import java.time.LocalDate;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class LeaveRepository extends InMemoryRepository<Leave, String> {

    public LeaveRepository() {
        super("Leave", Leave::id);
    }

    public boolean isOnLeave(String maidId, LocalDate date) {
        return !findAllMatching(leave -> leave.maidId().equals(maidId) && leave.covers(date)).isEmpty();
    }
}
