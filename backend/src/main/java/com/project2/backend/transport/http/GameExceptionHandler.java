package com.project2.backend.transport.http;

import com.project2.backend.game.session.GameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = SingleGameController.class)
public final class GameExceptionHandler {
    @ExceptionHandler(GameException.class)
    public ResponseEntity<ProblemDetail> gameError(GameException exception) {
        HttpStatus status = switch (exception.error()) {
            case UNKNOWN_GAME_TYPE, INVALID_GUESS -> HttpStatus.BAD_REQUEST;
            case GAME_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SUBMISSION_CONFLICT, GAME_FINISHED, ROUND_NOT_READY, ROUND_NOT_CURRENT, SUBMISSION_WINDOW_CLOSED -> HttpStatus.CONFLICT;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body or path is invalid");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(code);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
