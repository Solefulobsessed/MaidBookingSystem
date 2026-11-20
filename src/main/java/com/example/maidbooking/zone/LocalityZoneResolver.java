package com.example.maidbooking.zone;

import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ValidationException;

/** Resolves by exact (normalised) locality name. A geohash or polygon resolver can replace it. */
@Component
public class LocalityZoneResolver implements ZoneResolver {

    private final ZoneRepository zones;

    public LocalityZoneResolver(ZoneRepository zones) {
        this.zones = zones;
    }

    @Override
    public Zone resolve(String locality) {
        if (locality == null || locality.isBlank()) {
            throw new ValidationException("Locality is required");
        }
        return zones.findByLocality(locality)
                .orElseThrow(() -> new ValidationException("We don't serve locality '" + locality.trim() + "' yet"));
    }
}
