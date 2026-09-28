package com.project2.backend.lobby;

public final class LobbyException extends RuntimeException {
    private final LobbyError error;

    public LobbyException(LobbyError error, String message) {
        super(message);
        this.error = error;
    }

    public LobbyError error() {
        return error;
    }

    public String code() {
        return error.name();
    }
}
