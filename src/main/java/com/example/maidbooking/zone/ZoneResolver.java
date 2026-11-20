package com.example.maidbooking.zone;

/** Maps a customer's locality or address to the zone that serves it. */
public interface ZoneResolver {

    /** @throws com.example.maidbooking.common.error.ValidationException if no zone serves the locality */
    Zone resolve(String locality);
}
