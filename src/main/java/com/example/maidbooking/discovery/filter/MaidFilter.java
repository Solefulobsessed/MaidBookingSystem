package com.example.maidbooking.discovery.filter;

import java.util.List;

import com.example.maidbooking.supply.Maid;

/**
 * One customer requirement on the maid, e.g. "female" or "rating 4+". The same filters narrow a
 * preferred-maid search and are kept on the booking as hard constraints for allocation.
 */
public interface MaidFilter {

    boolean matches(Maid maid);

    /** e.g. "minRating=4.0", for logs and for storing on a booking. */
    String describe();

    static MaidFilter all(List<MaidFilter> filters) {
        List<MaidFilter> copy = List.copyOf(filters);
        return new MaidFilter() {
            @Override
            public boolean matches(Maid maid) {
                return copy.stream().allMatch(filter -> filter.matches(maid));
            }

            @Override
            public String describe() {
                return copy.stream().map(MaidFilter::describe).reduce((a, b) -> a + "&" + b).orElse("any");
            }
        };
    }
}
