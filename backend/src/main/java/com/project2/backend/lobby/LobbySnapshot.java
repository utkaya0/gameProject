package com.project2.backend.lobby;

import java.time.Instant;
import java.util.List;

public record LobbySnapshot(
        String code,
        String status,
        int maxPlayers,
        Instant expiresAt,
        String yourRole,
        String yourDisplayName,
        long sequence,
        List<Participant> participants,
        String gameId
) {
    public record Participant(String displayName, String role) {
    }
}
