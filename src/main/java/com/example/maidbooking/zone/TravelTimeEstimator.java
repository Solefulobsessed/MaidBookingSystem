package com.example.maidbooking.zone;

import java.time.Duration;
import java.util.Optional;

/** Estimated travel time between zones; empty when the destination is out of reach. */
public interface TravelTimeEstimator {

    Optional<Duration> estimate(Zone from, Zone to);
}
