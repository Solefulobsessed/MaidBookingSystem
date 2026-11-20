package com.example.maidbooking.discovery;

import java.time.LocalDateTime;
import java.util.List;

import com.example.maidbooking.common.Money;

/** Can a maid come now? If yes: when she'd start and the price. If not: the next bookable slots (today or tomorrow). */
public record InstantAvailability(boolean available, LocalDateTime start, Long etaMinutes, Money price,
                                  List<Alternative> alternatives) {

    public record Alternative(LocalDateTime start, SlotStatus status, Money price) {
    }
}
