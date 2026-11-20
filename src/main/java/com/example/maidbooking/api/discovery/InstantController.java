package com.example.maidbooking.api.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.discovery.InstantAvailability;
import com.example.maidbooking.discovery.InstantAvailabilityService;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@RestController
public class InstantController {

    private final InstantAvailabilityService instant;

    public InstantController(InstantAvailabilityService instant) {
        this.instant = instant;
    }

    @GetMapping("/api/v1/slots/instant")
    public InstantAvailability check(@RequestParam String locality, @RequestParam ServiceType serviceType,
                                     @RequestParam int durationMinutes,
                                     @RequestParam(defaultValue = "STANDARD") Tier tier) {
        return instant.check(locality, serviceType, durationMinutes, tier);
    }
}
