package com.example.maidbooking.capacity;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class CapacityReservationRepository extends InMemoryRepository<CapacityReservation, String> {

    public CapacityReservationRepository() {
        super("Capacity reservation", CapacityReservation::id);
    }
}
