package com.example.maidbooking.capacity;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class CapacityReservationRepository extends InMemoryRepository<CapacityReservation, String> {

    public CapacityReservationRepository() {
        super("Capacity reservation", CapacityReservation::id);
    }

    public List<CapacityReservation> findExpired(LocalDateTime now) {
        return findAllMatching(reservation -> reservation.isExpired(now));
    }

    public List<CapacityReservation> findExpiredInZone(String zoneId, LocalDateTime now) {
        return findAllMatching(reservation -> reservation.zoneId().equals(zoneId) && reservation.isExpired(now));
    }
}
