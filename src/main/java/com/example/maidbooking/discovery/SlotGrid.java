package com.example.maidbooking.discovery;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.example.maidbooking.common.Money;

/** Bookable start times for one zone, service, date and duration. Price is null when the slot is full. */
public record SlotGrid(String zoneId, LocalDate date, List<Slot> slots) {

    public record Slot(LocalTime start, SlotStatus status, Money price) {
    }
}
