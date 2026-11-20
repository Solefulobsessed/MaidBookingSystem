package com.example.maidbooking.allocation;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class AssignmentRepository extends InMemoryRepository<Assignment, String> {

    public AssignmentRepository() {
        super("Assignment", Assignment::id);
    }

    public List<Assignment> findLiveByMaid(String maidId) {
        return findAllMatching(a -> a.maidId().equals(maidId) && a.isLive());
    }

    public Optional<Assignment> findLiveByReference(String referenceId) {
        return findAllMatching(a -> a.referenceId().equals(referenceId) && a.isLive()).stream().findFirst();
    }
}
