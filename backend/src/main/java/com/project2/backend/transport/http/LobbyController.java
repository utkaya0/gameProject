package com.project2.backend.transport.http;

import com.project2.backend.guest.GuestSessionFilter;
import com.project2.backend.lobby.LobbyService;
import com.project2.backend.lobby.LobbySnapshot;
import com.project2.backend.game.multiplayer.MultiplayerGameSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.net.URI;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lobbies")
public final class LobbyController {
    private final LobbyService lobbies;
    private final IdempotencyService idempotency;

    public LobbyController(LobbyService lobbies, IdempotencyService idempotency) {
        this.lobbies = lobbies;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<LobbySnapshot> create(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                 @RequestBody CreateRequest request) {
        String code = idempotency.run(guestId, "lobby:create", key,
                request.displayName() + "\0" + request.maxPlayers(),
                () -> lobbies.create(guestId, request.displayName(), request.maxPlayers()).code());
        LobbySnapshot lobby = lobbies.get(guestId, code);
        return ResponseEntity.created(URI.create("/api/v1/lobbies/" + lobby.code())).body(lobby);
    }

    @PostMapping("/{code}/join")
    public LobbySnapshot join(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                              @PathVariable String code, @RequestBody JoinRequest request) {
        LobbySnapshot lobby = lobbies.join(guestId, code, request.displayName());
        return lobby;
    }

    @GetMapping("/{code}")
    public LobbySnapshot get(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                             @PathVariable String code) {
        return lobbies.get(guestId, code);
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<Void> leave(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                      @PathVariable String code) {
        lobbies.leave(guestId, code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/start")
    public MultiplayerGameSnapshot start(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                      @PathVariable String code) {
        return lobbies.start(guestId, code);
    }

    @PostMapping("/{code}/rematch")
    public MultiplayerGameSnapshot rematch(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                           @PathVariable String code) {
        return lobbies.rematch(guestId, code);
    }

    @GetMapping("/{code}/game")
    public MultiplayerGameSnapshot game(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                        @PathVariable String code) {
        return lobbies.game(guestId, code);
    }

    @PutMapping("/{code}/game/rounds/{roundNumber}/submission")
    public MultiplayerGameSnapshot submit(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
            @PathVariable String code, @PathVariable int roundNumber, @RequestBody GuessRequest request) {
        return lobbies.submit(guestId, code, roundNumber, request == null ? null : request.guess());
    }

    @PutMapping("/{code}/game/rounds/{roundNumber}/draft")
    public MultiplayerGameSnapshot saveDraft(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
            @PathVariable String code, @PathVariable int roundNumber, @RequestBody DraftRequest request) {
        return lobbies.saveDraft(guestId, code, roundNumber, request == null ? null : request.guess(),
                request == null ? -1 : request.revision());
    }

    @PostMapping("/{code}/game/ready")
    public MultiplayerGameSnapshot ready(@RequestAttribute(GuestSessionFilter.ATTRIBUTE) UUID guestId,
                                         @PathVariable String code) {
        return lobbies.ready(guestId, code);
    }

    public record CreateRequest(String displayName, Integer maxPlayers) {
    }

    public record JoinRequest(String displayName) {
    }

    public record GuessRequest(Map<String, Object> guess) {
    }

    public record DraftRequest(Map<String, Object> guess, long revision) {
    }
}
