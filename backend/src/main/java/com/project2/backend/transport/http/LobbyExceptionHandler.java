package com.project2.backend.transport.http;

import com.project2.backend.lobby.LobbyException;
import com.project2.backend.game.session.GameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LobbyController.class)
public final class LobbyExceptionHandler {
    @ExceptionHandler(LobbyException.class)
    public ResponseEntity<ProblemDetail> lobbyError(LobbyException exception) {
        HttpStatus status = switch (exception.error()) {
            case INVALID_DISPLAY_NAME, INVALID_MAX_PLAYERS, INVALID_CODE -> HttpStatus.BAD_REQUEST;
            case LOBBY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LOBBY_EXPIRED -> HttpStatus.GONE;
            case NOT_MEMBER, NOT_HOST -> HttpStatus.FORBIDDEN;
            case LOBBY_FULL, DISPLAY_NAME_TAKEN, GAME_NOT_AVAILABLE, GAME_IN_PROGRESS, NOT_ENOUGH_PLAYERS -> HttpStatus.CONFLICT;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid");
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ProblemDetail> gameError(GameException exception) {
        HttpStatus status = switch (exception.error()) {
            case UNKNOWN_GAME_TYPE, INVALID_GUESS -> HttpStatus.BAD_REQUEST;
            case GAME_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(code);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
