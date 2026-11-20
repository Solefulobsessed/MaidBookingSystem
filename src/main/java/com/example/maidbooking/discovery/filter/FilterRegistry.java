package com.example.maidbooking.discovery.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** All filter factories by key, collected from Spring. Unknown keys are rejected, so a typo isn't ignored. */
@Component
public class FilterRegistry {

    private final Map<String, MaidFilterFactory> factories;

    public FilterRegistry(List<MaidFilterFactory> factories) {
        this.factories = factories.stream().collect(Collectors.toMap(MaidFilterFactory::key, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Two filters use the key '" + a.key() + "'");
                }, TreeMap::new));
    }

    public List<MaidFilter> fromParams(Map<String, String> params) {
        List<MaidFilter> filters = new ArrayList<>();
        params.forEach((key, value) -> {
            MaidFilterFactory factory = factories.get(key);
            if (factory == null) {
                throw new InvalidFilterException(key, value, "unknown filter; supported: " + keys());
            }
            if (value == null || value.isBlank()) {
                throw new InvalidFilterException(key, value, "value is empty");
            }
            filters.add(factory.create(value.trim()));
        });
        return filters;
    }

    public Set<String> keys() {
        return factories.keySet();
    }
}
