package com.example.maidbooking.allocation;

import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.Shift;

/**
 * A maid who could take the job: her shift that covers it, her calendar, and the time the job would
 * block for her (travel from where she'll be, then the work).
 */
public record Candidate(Maid maid, Shift shift, MaidCalendar calendar, TimeSlot blocked) {
}
