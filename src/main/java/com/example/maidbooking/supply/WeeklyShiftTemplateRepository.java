package com.example.maidbooking.supply;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class WeeklyShiftTemplateRepository extends InMemoryRepository<WeeklyShiftTemplate, String> {

    public WeeklyShiftTemplateRepository() {
        super("Shift template", WeeklyShiftTemplate::maidId);
    }
}
