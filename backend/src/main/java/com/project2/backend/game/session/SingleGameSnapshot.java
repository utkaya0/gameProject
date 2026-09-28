package com.project2.backend.game.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SingleGameSnapshot(
        UUID id,
        String gameType,
        String mode,
        GameStatus status,
        String configVersion,
        int currentRound,
        int totalRounds,
        GamePhase phase,
        Instant serverTime,
        Instant phaseEndsAt,
        GameData gameData,
        String yourDraft,
        double totalScore,
        List<RoundResult> revealedRounds
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoundResult(
            int roundNumber,
            double score,
            Long responseTimeMs,
            GameData gameData
    ) {
    }
}
