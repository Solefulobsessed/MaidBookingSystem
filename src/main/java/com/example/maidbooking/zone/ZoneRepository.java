package com.example.maidbooking.zone;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class ZoneRepository extends InMemoryRepository<Zone, String> {

    public ZoneRepository() {
        super("Zone", Zone::id);
    }

    public Optional<Zone> findByLocality(String locality) {
        return findAllMatching(zone -> zone.covers(locality)).stream().findFirst();
    }
}
