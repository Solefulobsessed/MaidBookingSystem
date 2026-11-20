package com.example.maidbooking.discovery;

import java.util.List;
import java.util.Set;

import com.example.maidbooking.common.Money;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Tier;

/** One page of maids free for the requested slot, best rated first. */
public record MaidSearchResult(List<Item> items, int page, int size, int total) {

    public record Item(String maidId, String name, Gender gender, Tier tier, double rating, Set<String> tags,
                       String zoneId, Money quotedPrice) {
    }
}
