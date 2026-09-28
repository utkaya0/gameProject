package com.project2.backend.guest;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public final class GuestSessionService {
    private static final Duration SESSION_LIFETIME = Duration.ofDays(7);
    private final ConcurrentMap<UUID, Instant> sessions = new ConcurrentHashMap<>();

    public Resolution resolve(String cookieValue) {
        if (cookieValue != null) {
            try {
                UUID id = UUID.fromString(cookieValue);
                if (refresh(id)) {
                    return new Resolution(id, false);
                }
            } catch (IllegalArgumentException ignored) {
                // An unknown or malformed cookie gets a new anonymous session.
            }
        }
        UUID id = UUID.randomUUID();
        sessions.put(id, Instant.now());
        return new Resolution(id, true);
    }

    public UUID findExisting(String cookieValue) {
        if (cookieValue == null) return null;
        try {
            UUID id = UUID.fromString(cookieValue);
            return refresh(id) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean refresh(UUID id) {
        Instant now = Instant.now();
        return sessions.computeIfPresent(id, (ignored, seen) ->
                seen.plus(SESSION_LIFETIME).isAfter(now) ? now : null) != null;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanup() {
        Instant cutoff = Instant.now().minus(SESSION_LIFETIME);
        sessions.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    public record Resolution(UUID guestId, boolean newSession) {
    }
}
