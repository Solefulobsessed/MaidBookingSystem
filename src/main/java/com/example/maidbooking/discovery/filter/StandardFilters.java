package com.example.maidbooking.discovery.filter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.Tier;

/** The filters the spec asks for. Each is one factory bean; adding a filter never touches the others. */
@Configuration
public class StandardFilters {

    @Bean
    public MaidFilterFactory minRatingFilter() {
        return factory("minRating", raw -> {
            double min = parse("minRating", raw, Double::parseDouble);
            if (min < 0 || min > 5) {
                throw new InvalidFilterException("minRating", raw, "must be between 0 and 5");
            }
            return maid -> maid.rating() >= min;
        });
    }

    @Bean
    public MaidFilterFactory tierFilter() {
        return factory("tier", raw -> {
            Tier tier = parse("tier", raw, value -> Tier.valueOf(value.toUpperCase(Locale.ROOT)));
            return maid -> maid.tier() == tier;
        });
    }

    @Bean
    public MaidFilterFactory genderFilter() {
        return factory("gender", raw -> {
            Gender gender = parse("gender", raw, value -> Gender.valueOf(value.toUpperCase(Locale.ROOT)));
            return maid -> maid.gender() == gender;
        });
    }

    /** Comma-separated tags; the maid must have every one, e.g. "deep-cleaning,pet-friendly". */
    @Bean
    public MaidFilterFactory skillsFilter() {
        return factory("skills", raw -> {
            Set<String> tags = Arrays.stream(raw.split(",")).map(String::trim).filter(t -> !t.isEmpty())
                    .collect(Collectors.toSet());
            return maid -> tags.stream().allMatch(maid::hasTag);
        });
    }

    private static MaidFilterFactory factory(String key, Function<String, Predicate<Maid>> build) {
        return new MaidFilterFactory() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public MaidFilter create(String rawValue) {
                Predicate<Maid> test = build.apply(rawValue);
                return new MaidFilter() {
                    @Override
                    public boolean matches(Maid maid) {
                        return test.test(maid);
                    }

                    @Override
                    public String describe() {
                        return key + "=" + rawValue;
                    }
                };
            }
        };
    }

    private static <T> T parse(String key, String raw, Function<String, T> parser) {
        try {
            return parser.apply(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidFilterException(key, raw, "not a valid value");
        }
    }
}
