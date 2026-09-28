package com.project2.backend.game.multiplayer;

import com.project2.backend.game.session.GameData;
import com.project2.backend.game.session.GamePhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MultiplayerGameSnapshot(UUID id, String lobbyCode, String gameType, String status,
        String configVersion, int currentRound, int totalRounds, GamePhase phase,
        Instant serverTime, Instant phaseEndsAt, GameData gameData, boolean yourSubmissionReceived,
        String yourDraft,
        List<Player> players, List<RoundResult> revealedRounds) {
    public record Player(String displayName, double totalScore, Double roundScore, boolean submitted, boolean ready) {}
    public record RoundResult(int roundNumber, GameData gameData, List<Score> scores) {}
    public record Score(String displayName, double score) {}
}
