package com.project2.backend.game.color;

import com.project2.backend.common.GameConfig;

public record ColorGameConfig(GameConfig session, double scoreScale) {
    public ColorGameConfig {
        if (session == null || !Double.isFinite(scoreScale) || scoreScale <= 0) {
            throw new IllegalArgumentException("Invalid color game configuration");
        }
    }

    public static ColorGameConfig initial() {
        return new ColorGameConfig(new GameConfig("color-v1", 5, 3_000, 750, 10_000, 3_000), 20.0);
    }
}
