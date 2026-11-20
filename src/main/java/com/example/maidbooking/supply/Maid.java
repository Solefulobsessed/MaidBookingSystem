package com.example.maidbooking.supply;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.ValidationException;

/**
 * A service professional. Holds no availability: that lives in shifts and assignments.
 * Status changes only through {@link #activate()} and {@link #suspend()}.
 */
public class Maid {

    private static final Pattern PHONE = Pattern.compile("(\\+91)?[6-9]\\d{9}");

    private final String id;
    private final String name;
    private final Gender gender;
    private final String phone;
    private final Set<ServiceType> skills;
    private final Set<String> tags;
    private final Tier tier;
    private final String homeZoneId;
    private final double rating;
    private MaidStatus status;

    public Maid(String id, String name, Gender gender, String phone, Set<ServiceType> skills,
                Set<String> tags, Tier tier, String homeZoneId, double rating) {
        this.id = Objects.requireNonNull(id, "id");
        this.gender = Objects.requireNonNull(gender, "gender");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.homeZoneId = Objects.requireNonNull(homeZoneId, "homeZoneId");
        if (name == null || name.isBlank()) {
            throw new ValidationException("Maid name is required");
        }
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new ValidationException("Phone must be a 10-digit Indian mobile number");
        }
        if (skills == null || skills.isEmpty()) {
            throw new ValidationException("Maid needs at least one skill");
        }
        if (rating < 0 || rating > 5) {
            throw new ValidationException("Rating must be between 0 and 5");
        }
        this.name = name.trim();
        this.phone = phone;
        this.skills = Set.copyOf(skills);
        this.tags = tags == null ? Set.of()
                : tags.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        this.rating = rating;
        this.status = MaidStatus.ONBOARDING;
    }

    public void activate() {
        if (status == MaidStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Maid", status, "activate");
        }
        status = MaidStatus.ACTIVE;
    }

    public void suspend() {
        if (status != MaidStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Maid", status, "suspend");
        }
        status = MaidStatus.SUSPENDED;
    }

    public boolean offers(ServiceType service) {
        return skills.contains(service);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isActive() {
        return status == MaidStatus.ACTIVE;
    }

    /** A rating of 0 means no reviews yet. */
    public boolean isRated() {
        return rating > 0;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Gender gender() { return gender; }
    public String phone() { return phone; }
    public Set<ServiceType> skills() { return skills; }
    public Set<String> tags() { return tags; }
    public Tier tier() { return tier; }
    public String homeZoneId() { return homeZoneId; }
    public double rating() { return rating; }
    public MaidStatus status() { return status; }
}
