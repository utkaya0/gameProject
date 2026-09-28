package com.project2.backend.common;

/** Timing and round count are fixed when a game starts. */
public record GameConfig(
        String version,
        int totalRounds,
        long previewMs,
        long transitionMs,
        long inputMs,
        long revealMs
) {
    public GameConfig {
        if (version == null || version.isBlank()
                || totalRounds <= 0
                || previewMs <= 0 || transitionMs <= 0 || inputMs <= 0 || revealMs <= 0) {
            throw new IllegalArgumentException("Invalid game configuration");
        }
    }
}
