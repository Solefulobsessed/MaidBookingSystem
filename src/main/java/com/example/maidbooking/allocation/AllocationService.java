package com.example.maidbooking.allocation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.maidbooking.common.lock.LockKey;
import com.example.maidbooking.common.lock.LockRegistry;
import com.example.maidbooking.supply.ShiftRepository;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

/**
 * Assigns a maid to a job. Candidates are found and ranked without locks; the chosen maid is then re-checked
 * under her lock, and if someone else took her time meanwhile, the next candidate is tried.
 * Takes only maid locks, so callers must not hold a job lock (lock order: zone, maid, job).
 */
@Service
public class AllocationService {

    private final CandidateFinder finder;
    private final AllocationStrategy strategy;
    private final DispatchStrategy dispatchStrategy;
    private final AssignmentRepository assignments;
    private final ShiftRepository shifts;
    private final ZoneRepository zones;
    private final LockRegistry locks;

    public AllocationService(CandidateFinder finder, AllocationStrategy strategy, DispatchStrategy dispatch,
                             AssignmentRepository assignments, ShiftRepository shifts, ZoneRepository zones,
                             LockRegistry locks) {
        this.finder = finder;
        this.strategy = strategy;
        this.dispatchStrategy = dispatch;
        this.assignments = assignments;
        this.shifts = shifts;
        this.zones = zones;
        this.locks = locks;
    }

    /**
     * @param initialStatus HELD for an unpaid instant job, otherwise ACTIVE
     * @return the assignment, or empty if no maid can take the job right now
     */
    public Optional<Assignment> allocate(AllocationRequest request, AssignmentStatus initialStatus) {
        return assignFirst(strategy.order(finder.find(request), request), request, initialStatus);
    }

    /**
     * Instant: holds the nearest idle maid who can start exactly at {@code request.work()}, as planned from the
     * same dispatch options a moment earlier. Empty if all of them were taken meanwhile.
     */
    public Optional<Assignment> dispatch(AllocationRequest request, LocalDateTime now) {
        List<Candidate> nearest = dispatchStrategy.options(request.zoneId(), request.service(), request.work().duration(),
                        request.constraints(), now).stream()
                .filter(option -> option.work().equals(request.work()))
                .map(DispatchOption::candidate)
                .toList();
        return assignFirst(nearest, request, AssignmentStatus.HELD);
    }

    private Optional<Assignment> assignFirst(List<Candidate> ranked, AllocationRequest request,
                                             AssignmentStatus initialStatus) {
        Optional<Assignment> existing = assignments.findLiveByReference(request.referenceId());
        if (existing.isPresent()) {
            return existing;
        }
        Zone target = zones.getById(request.zoneId());
        for (Candidate candidate : ranked) {
            Optional<Assignment> assigned = locks.callWith(LockKey.maid(candidate.maid().id()), () ->
                    finder.candidate(shifts.getById(candidate.shift().id()), target, request)
                            .map(fresh -> assignments.save(new Assignment("as-" + UUID.randomUUID().toString().substring(0, 8),
                                    request.referenceId(), fresh.maid().id(), fresh.shift().id(), request.zoneId(),
                                    request.work(), fresh.blocked(), initialStatus))));
            if (assigned.isPresent()) {
                return assigned;
            }
        }
        return Optional.empty();
    }

    /** Frees the maid's time for this job, e.g. on cancellation or drop-out. No-op if nothing is assigned. */
    public Optional<Assignment> release(String referenceId) {
        return assignments.findLiveByReference(referenceId).map(assignment -> {
            locks.runWith(LockKey.maid(assignment.maidId()), assignment::release);
            return assignment;
        });
    }

    /** An unpaid instant job was paid for: its held maid is now committed. */
    public Optional<Assignment> activate(String referenceId) {
        return assignments.findLiveByReference(referenceId).map(assignment -> {
            locks.runWith(LockKey.maid(assignment.maidId()), assignment::activate);
            return assignment;
        });
    }
}
