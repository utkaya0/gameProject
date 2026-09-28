package com.project2.backend.game.session;

public final class GameException extends RuntimeException {
    private final GameError error;

    public GameException(GameError error, String message) {
        super(message);
        this.error = error;
    }

    public GameError error() {
        return error;
    }

    public String code() {
        return error.name();
    }
}
