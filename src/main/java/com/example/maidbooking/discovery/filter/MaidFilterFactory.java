package com.example.maidbooking.discovery.filter;

/** Turns one query parameter into a {@link MaidFilter}. Add a filter by adding one of these as a bean. */
public interface MaidFilterFactory {

    /** The query parameter name, e.g. "minRating". */
    String key();

    /** @throws InvalidFilterException if the value can't be parsed */
    MaidFilter create(String rawValue);
}
