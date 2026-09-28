package com.project2.backend.game.session;

public enum GameError {
    UNKNOWN_GAME_TYPE,
    GAME_NOT_FOUND,
    INVALID_GUESS,
    SUBMISSION_CONFLICT,
    GAME_FINISHED,
    ROUND_NOT_READY,
    ROUND_NOT_CURRENT,
    SUBMISSION_WINDOW_CLOSED
}
