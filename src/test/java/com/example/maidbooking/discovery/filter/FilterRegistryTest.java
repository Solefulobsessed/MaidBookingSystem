package com.example.maidbooking.discovery.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

class FilterRegistryTest {

    private final StandardFilters standard = new StandardFilters();
    private final FilterRegistry registry = new FilterRegistry(List.of(standard.minRatingFilter(),
            standard.tierFilter(), standard.genderFilter(), standard.skillsFilter()));

    private static Maid maid(String id, Gender gender, Tier tier, double rating, String... tags) {
        return new Maid(id, "Maid " + id, gender, "98765432" + id.substring(id.length() - 2), Set.of(ServiceType.CLEANING),
                Set.of(tags), tier, "kor-5", rating);
    }

    private final Maid lakshmi = maid("m-01", Gender.FEMALE, Tier.PREMIUM, 4.6, "deep-cleaning", "pet-friendly");
    private final Maid ravi = maid("m-02", Gender.MALE, Tier.STANDARD, 4.2, "deep-cleaning");
    private final Maid asha = maid("m-03", Gender.FEMALE, Tier.STANDARD, 0.0);

    private List<Maid> matching(Map<String, String> params) {
        MaidFilter all = MaidFilter.all(registry.fromParams(params));
        return List.of(lakshmi, ravi, asha).stream().filter(all::matches).toList();
    }

    @Test
    void eachFilterOnItsOwn() {
        assertThat(matching(Map.of("minRating", "4.5"))).containsExactly(lakshmi);
        assertThat(matching(Map.of("tier", "standard"))).containsExactly(ravi, asha);
        assertThat(matching(Map.of("gender", "FEMALE"))).containsExactly(lakshmi, asha);
        assertThat(matching(Map.of("skills", "deep-cleaning"))).containsExactly(lakshmi, ravi);
        assertThat(matching(Map.of("skills", "Deep-Cleaning, pet-friendly"))).containsExactly(lakshmi);
    }

    @Test
    void filtersCombineWithAnd() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("gender", "FEMALE");
        params.put("minRating", "4");
        params.put("skills", "deep-cleaning");

        assertThat(matching(params)).containsExactly(lakshmi);
        assertThat(MaidFilter.all(registry.fromParams(params)).describe())
                .isEqualTo("gender=FEMALE&minRating=4&skills=deep-cleaning");
    }

    @Test
    void unratedMaidsFailAnyPositiveMinimum() {
        assertThat(matching(Map.of("minRating", "0.1"))).doesNotContain(asha);
        assertThat(matching(Map.of("minRating", "0"))).contains(asha);
    }

    @Test
    void noFiltersMatchesEveryone() {
        assertThat(matching(Map.of())).hasSize(3);
        assertThat(MaidFilter.all(List.of()).describe()).isEqualTo("any");
    }

    @Test
    void unknownKeyIsRejectedWithTheSupportedList() {
        assertThatThrownBy(() -> registry.fromParams(Map.of("minRatng", "4")))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("minRatng")
                .hasMessageContaining("[gender, minRating, skills, tier]");
    }

    @Test
    void badValuesAreRejected() {
        assertThatThrownBy(() -> registry.fromParams(Map.of("minRating", "four"))).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> registry.fromParams(Map.of("minRating", "6"))).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> registry.fromParams(Map.of("tier", "GOLD"))).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> registry.fromParams(Map.of("gender", " "))).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void twoFactoriesWithTheSameKeyFailAtStartup() {
        assertThatThrownBy(() -> new FilterRegistry(List.of(standard.tierFilter(), standard.tierFilter())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tier");
    }

    @Test
    void aNewFilterIsJustAnotherFactory() {
        MaidFilterFactory premiumOnly = new MaidFilterFactory() {
            @Override
            public String key() {
                return "premiumOnly";
            }

            @Override
            public MaidFilter create(String rawValue) {
                return new MaidFilter() {
                    @Override
                    public boolean matches(Maid maid) {
                        return maid.tier() == Tier.PREMIUM;
                    }

                    @Override
                    public String describe() {
                        return "premiumOnly";
                    }
                };
            }
        };
        FilterRegistry extended = new FilterRegistry(List.of(standard.genderFilter(), premiumOnly));

        assertThat(extended.keys()).containsExactly("gender", "premiumOnly");
        assertThat(extended.fromParams(Map.of("premiumOnly", "yes")).get(0).matches(lakshmi)).isTrue();
    }
}
