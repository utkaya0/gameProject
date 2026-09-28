package com.project2.backend.game.session;

import com.project2.backend.common.GameConfig;
import java.time.Instant;

public record RoundTimeline(
        Instant startedAt,
        Instant previewUntil,
        Instant inputOpensAt,
        Instant inputClosesAt,
        Instant revealUntil
) {
    public static RoundTimeline startingAt(Instant startedAt, GameConfig config) {
        Instant previewUntil = startedAt.plusMillis(config.previewMs());
        Instant inputOpensAt = previewUntil.plusMillis(config.transitionMs());
        Instant inputClosesAt = inputOpensAt.plusMillis(config.inputMs());
        return new RoundTimeline(startedAt, previewUntil, inputOpensAt, inputClosesAt,
                inputClosesAt.plusMillis(config.revealMs()));
    }

    public GamePhase phaseAt(Instant now) {
        if (now.isBefore(previewUntil)) return GamePhase.PREVIEW;
        if (now.isBefore(inputOpensAt)) return GamePhase.TRANSITION;
        if (now.isBefore(inputClosesAt)) return GamePhase.INPUT;
        return GamePhase.REVEAL;
    }

    public Instant endOf(GamePhase phase) {
        return switch (phase) {
            case PREVIEW -> previewUntil;
            case TRANSITION -> inputOpensAt;
            case INPUT -> inputClosesAt;
            case REVEAL -> revealUntil;
            case COMPLETED -> null;
        };
    }
}
