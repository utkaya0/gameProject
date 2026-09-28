package com.project2.backend.game.session;

public record GameEvaluation(double score, GameData result) {
    public GameEvaluation {
        if (!Double.isFinite(score) || score < 0 || score > 10 || result == null) {
            throw new IllegalArgumentException("Invalid game evaluation");
        }
    }
}
