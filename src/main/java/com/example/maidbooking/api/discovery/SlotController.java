package com.example.maidbooking.api.discovery;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.discovery.SlotGrid;
import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@RestController
@RequestMapping("/api/v1/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public SlotGrid slots(@RequestParam String locality, @RequestParam ServiceType serviceType,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam int durationMinutes,
                          @RequestParam(defaultValue = "STANDARD") Tier tier) {
        return slotService.slots(locality, serviceType, date, durationMinutes, tier);
    }
}
