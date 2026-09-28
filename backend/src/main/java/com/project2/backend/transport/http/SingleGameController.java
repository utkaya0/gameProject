package com.project2.backend.transport.http;

import com.project2.backend.game.session.SingleGameService;
import com.project2.backend.game.session.SingleGameSnapshot;
import com.project2.backend.guest.GuestSessionFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public final class SingleGameController {
    private final SingleGameService service;
    private final IdempotencyService idempotency;

    public SingleGameController(SingleGameService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping("/single-games")
    public ResponseEntity<SingleGameSnapshot> start(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                    @RequestBody(required = false) StartRequest request) {
        String gameType = request == null || request.gameType() == null
                ? "COLOR_GUESS" : request.gameType();
        UUID id = idempotency.run(guestId, "single:start", key, gameType,
                () -> service.start(gameType).id());
        SingleGameSnapshot game = service.get(id);
        return ResponseEntity.created(URI.create("/api/v1/games/" + game.id())).body(game);
    }

    @GetMapping("/games/{id}")
    public SingleGameSnapshot get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/games/{id}/continue")
    public SingleGameSnapshot continueGame(@PathVariable UUID id) {
        return service.continueGame(id);
    }

    @PutMapping("/games/{id}/rounds/{roundNumber}/submission")
    public SingleGameSnapshot submit(@PathVariable UUID id, @PathVariable int roundNumber,
                                     @RequestBody SubmissionRequest request) {
        Map<String, Object> guess = request == null ? null : request.guess();
        if (guess == null && request != null && request.guessColor() != null) {
            guess = Map.of("color", request.guessColor());
        }
        return service.submit(id, roundNumber, guess);
    }

    @PutMapping("/games/{id}/rounds/{roundNumber}/draft")
    public SingleGameSnapshot saveDraft(@PathVariable UUID id, @PathVariable int roundNumber,
                                        @RequestBody DraftRequest request) {
        return service.saveDraft(id, roundNumber, request == null ? null : request.guess(),
                request == null ? -1 : request.revision());
    }

    public record StartRequest(String gameType) {
    }

    public record SubmissionRequest(Map<String, Object> guess, String guessColor) {
    }

    public record DraftRequest(Map<String, Object> guess, long revision) {
    }
}
