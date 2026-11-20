package com.example.maidbooking.allocation;

import java.time.Duration;

import com.example.maidbooking.common.TimeSlot;

/** A maid who can be sent now: the candidate, the job's time if she goes, and how soon she'd arrive. */
public record DispatchOption(Candidate candidate, TimeSlot work, Duration eta) {
}
