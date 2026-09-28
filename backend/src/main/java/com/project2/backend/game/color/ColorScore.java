package com.project2.backend.game.color;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Raw values are retained; rounding is only for display. */
public record ColorScore(double score, double colorDistance) {
    public BigDecimal displayScore() {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }
}
