package com.project2.backend.game.color;

public final class ColorScorer {
    private final ColorGameConfig config;

    public ColorScorer(ColorGameConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Game configuration is required");
        }
        this.config = config;
    }

    public ColorScore score(String targetHex, String guessHex) {
        LabColor target = ColorDifference.toLab(SrgbColor.parse(targetHex));
        LabColor guess = ColorDifference.toLab(SrgbColor.parse(guessHex));
        double distance = ColorDifference.deltaE00(target, guess);
        double ratio = distance / config.scoreScale();
        double rawScore = 10.0 * Math.exp(-(ratio * ratio));
        return new ColorScore(Math.max(0.0, Math.min(10.0, rawScore)), distance);
    }
}
