package com.project2.backend.lobby;

import java.time.Instant;
import java.util.Map;

public record LobbyEvent(
        String code,
        String type,
        long sequence,
        Instant serverTime,
        String gameId,
        Map<String, String> payload
) {
}
