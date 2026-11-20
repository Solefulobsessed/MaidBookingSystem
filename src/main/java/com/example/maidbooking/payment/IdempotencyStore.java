package com.example.maidbooking.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.ValidationException;

/**
 * Runs an action at most once per idempotency key. A repeat with the same request gets the first
 * result back (or the same business error); the same key with a different request is rejected.
 */
@Component
public class IdempotencyStore {

    private enum State { IN_PROGRESS, COMPLETED, FAILED }

    private record Entry(String requestHash, State state, Object response, DomainException error, Instant createdAt) {
        boolean expired(Instant now, Duration ttl) {
            return createdAt.plus(ttl).isBefore(now);
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyStore(Clock clock, @Value("${app.idempotency.ttl-hours:24}") long ttlHours) {
        this.clock = clock;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public <T> IdempotentResult<T> execute(String key, String requestHash, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required");
        }
        Instant now = clock.instant();
        Entry claim = new Entry(requestHash, State.IN_PROGRESS, null, null, now);
        Entry current = entries.compute(key, (k, old) -> old == null || old.expired(now, ttl) ? claim : old);
        if (current == claim) {
            return new IdempotentResult<>(runAndRecord(key, claim, action), false);
        }
        return replay(key, requestHash, current);
    }

    /** Drops records older than the TTL; called by a scheduled sweeper. */
    public void purgeExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> entry.state() != State.IN_PROGRESS && entry.expired(now, ttl));
    }

    public static String fingerprint(String canonicalRequest) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private <T> T runAndRecord(String key, Entry claim, Supplier<T> action) {
        try {
            T response = action.get();
            entries.put(key, new Entry(claim.requestHash(), State.COMPLETED, response, null, claim.createdAt()));
            return response;
        } catch (DomainException businessError) {
            entries.put(key, new Entry(claim.requestHash(), State.FAILED, null, businessError, claim.createdAt()));
            throw businessError;
        } catch (RuntimeException unexpected) {
            entries.remove(key, claim);
            throw unexpected;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> IdempotentResult<T> replay(String key, String requestHash, Entry entry) {
        if (!entry.requestHash().equals(requestHash)) {
            throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key " + key + " was already used for a different request", Map.of("key", key));
        }
        if (entry.state() == State.IN_PROGRESS) {
            throw new ConflictException(ErrorCode.REQUEST_IN_PROGRESS,
                    "A request with idempotency key " + key + " is still being processed", Map.of("key", key));
        }
        if (entry.state() == State.FAILED) {
            throw entry.error();
        }
        return new IdempotentResult<>((T) entry.response(), true);
    }
}
