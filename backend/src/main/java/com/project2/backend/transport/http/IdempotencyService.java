package com.project2.backend.transport.http;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public final class IdempotencyService {
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private final ConcurrentMap<CacheKey, Entry> entries = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T run(UUID guestId, String action, String key, String payload, Supplier<T> operation) {
        if (key == null) return operation.get();
        if (!key.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }
        CacheKey cacheKey = new CacheKey(guestId, action, key);
        Entry entry = entries.compute(cacheKey, (ignored, previous) -> {
            if (previous != null && Instant.now().isBefore(previous.expiresAt)) {
                if (!previous.payload.equals(payload)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used with another request");
                }
                return previous;
            }
            return new Entry(payload, operation.get(), Instant.now().plus(LIFETIME));
        });
        return (T) entry.result;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
    }

    private record CacheKey(UUID guestId, String action, String key) {}
    private record Entry(String payload, Object result, Instant expiresAt) {}
}
