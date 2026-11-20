package com.example.maidbooking.zone;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.maidbooking.common.error.ValidationException;

/**
 * A micro-market (under ~1 km across) that maids work in and customers book against.
 * Localities are stored normalised so "  Koramangala " and "koramangala" match.
 */
public record Zone(String id, String name, Set<String> localities, Set<String> neighbourZoneIds) {

    public Zone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (localities == null || localities.isEmpty()) {
            throw new ValidationException("Zone " + id + " needs at least one locality");
        }
        localities = localities.stream().map(Zone::normalise).collect(Collectors.toUnmodifiableSet());
        neighbourZoneIds = neighbourZoneIds == null ? Set.of() : Set.copyOf(neighbourZoneIds);
        if (neighbourZoneIds.contains(id)) {
            throw new ValidationException("Zone " + id + " cannot be its own neighbour");
        }
    }

    public boolean covers(String locality) {
        return localities.contains(normalise(locality));
    }

    public boolean isNeighbourOf(Zone other) {
        return neighbourZoneIds.contains(other.id) || other.neighbourZoneIds.contains(id);
    }

    public static String normalise(String locality) {
        return locality.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
