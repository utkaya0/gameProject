package com.project2.backend.lobby;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import com.project2.backend.game.multiplayer.MultiplayerGameService;
import com.project2.backend.game.multiplayer.MultiplayerGameSnapshot;
import com.project2.backend.game.color.ColorGameDefinition;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public final class LobbyService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration LOBBY_LIFETIME = Duration.ofHours(2);
    private final ConcurrentMap<String, Lobby> lobbies = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final MultiplayerGameService games;

    @Autowired
    public LobbyService(ApplicationEventPublisher events, MultiplayerGameService games) {
        this(Clock.systemUTC(), events, games);
    }

    public LobbyService(ApplicationEventPublisher events) {
        this(Clock.systemUTC(), events);
    }

    LobbyService(Clock clock) {
        this(clock, event -> {});
    }

    LobbyService(Clock clock, ApplicationEventPublisher events) {
        this(clock, events, new MultiplayerGameService(clock, List.of(new ColorGameDefinition())));
    }

    LobbyService(Clock clock, ApplicationEventPublisher events, MultiplayerGameService games) {
        this.clock = clock;
        this.events = events;
        this.games = games;
    }

    public LobbySnapshot create(UUID guestId, String rawName, Integer requestedMaxPlayers) {
        String name = validName(rawName);
        int maxPlayers = requestedMaxPlayers == null ? 8 : requestedMaxPlayers;
        if (maxPlayers < 2 || maxPlayers > 8) {
            throw new LobbyException(LobbyError.INVALID_MAX_PLAYERS, "Lobby capacity must be between 2 and 8");
        }
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = newCode();
            Lobby lobby = new Lobby(code, maxPlayers, now.plus(LOBBY_LIFETIME));
            lobby.members.put(guestId, new Member(name, "HOST"));
            if (lobbies.putIfAbsent(code, lobby) == null) {
                synchronized (lobby) {
                    return lobby.snapshot(guestId);
                }
            }
        }
        throw new IllegalStateException("Could not generate a unique lobby code");
    }

    public LobbySnapshot join(UUID guestId, String rawCode, String rawName) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureJoinable(lobby);
            String name = validName(rawName);
            if (lobby.members.containsKey(guestId)) return lobby.snapshot(guestId);
            if (lobby.members.size() >= lobby.maxPlayers) {
                throw new LobbyException(LobbyError.LOBBY_FULL, "Lobby is full");
            }
            if (lobby.members.values().stream().anyMatch(member -> member.name.equalsIgnoreCase(name))) {
                throw new LobbyException(LobbyError.DISPLAY_NAME_TAKEN, "Display name is already in use");
            }
            lobby.members.put(guestId, new Member(name, "PLAYER"));
            publish(lobby, "PLAYER_JOINED");
            return lobby.snapshot(guestId);
        }
    }

    public LobbySnapshot get(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureActive(lobby);
            requireMember(lobby, guestId);
            return lobby.snapshot(guestId);
        }
    }

    public void leave(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureActive(lobby);
            Member departing = requireMember(lobby, guestId);
            if ("IN_GAME".equals(lobby.status) && lobby.gameId != null
                    && games.leave(UUID.fromString(lobby.gameId), guestId)) {
                lobby.status = "FINISHED";
            }
            lobby.members.remove(guestId);
            lobby.currentGamePlayers.remove(guestId);
            if (lobby.members.isEmpty()) {
                lobby.status = "EXPIRED";
                lobbies.remove(lobby.code, lobby);
                discardGame(lobby);
            } else if ("HOST".equals(departing.role)) {
                lobby.members.values().iterator().next().role = "HOST";
            }
            publish(lobby, "PLAYER_LEFT");
        }
    }

    public void assertHostForStart(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureWaiting(lobby);
            Member member = requireMember(lobby, guestId);
            if (!"HOST".equals(member.role)) {
                throw new LobbyException(LobbyError.NOT_HOST, "Only the host can start a game");
            }
        }
    }

    public MultiplayerGameSnapshot start(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureWaiting(lobby);
            if (!"HOST".equals(requireMember(lobby, guestId).role))
                throw new LobbyException(LobbyError.NOT_HOST, "Only the host can start a game");
            if (lobby.members.size() < 2)
                throw new LobbyException(LobbyError.NOT_ENOUGH_PLAYERS, "At least two players are required");
            Map<UUID, String> roster = new LinkedHashMap<>();
            lobby.members.forEach((id, member) -> roster.put(id, member.name));
            MultiplayerGameSnapshot game = games.start(lobby.code, roster, guestId);
            lobby.gameId = game.id().toString();
            lobby.currentGamePlayers.clear();
            lobby.currentGamePlayers.addAll(roster.keySet());
            lobby.status = "IN_GAME";
            publish(lobby, "GAME_STARTED");
            return game;
        }
    }

    public MultiplayerGameSnapshot rematch(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            ensureActive(lobby);
            if (!"HOST".equals(requireMember(lobby, guestId).role))
                throw new LobbyException(LobbyError.NOT_HOST, "Only the host can start a rematch");
            if (!"FINISHED".equals(lobby.status))
                throw new LobbyException(LobbyError.GAME_IN_PROGRESS, "Previous game has not finished");
            if (lobby.members.size() < 2)
                throw new LobbyException(LobbyError.NOT_ENOUGH_PLAYERS, "At least two players are required");
            Map<UUID, String> roster = new LinkedHashMap<>();
            lobby.members.forEach((id, member) -> roster.put(id, member.name));
            MultiplayerGameSnapshot game = games.start(lobby.code, roster, guestId);
            discardGame(lobby);
            lobby.gameId = game.id().toString();
            lobby.currentGamePlayers.clear();
            lobby.currentGamePlayers.addAll(roster.keySet());
            lobby.status = "IN_GAME";
            publish(lobby, "GAME_STARTED");
            return game;
        }
    }

    public MultiplayerGameSnapshot game(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            return activeGame(lobby, guestId);
        }
    }

    public MultiplayerGameSnapshot submit(UUID guestId, String rawCode, int round, Map<String, Object> guess) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            activeGame(lobby, guestId);
            MultiplayerGameSnapshot result = games.submit(UUID.fromString(lobby.gameId), guestId, round, guess);
            publish(lobby, "GAME_UPDATED");
            return result;
        }
    }

    public MultiplayerGameSnapshot saveDraft(UUID guestId, String rawCode, int round,
                                             Map<String, Object> guess, long revision) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            activeGame(lobby, guestId);
            return games.saveDraft(UUID.fromString(lobby.gameId), guestId, round, guess, revision);
        }
    }

    public MultiplayerGameSnapshot ready(UUID guestId, String rawCode) {
        Lobby lobby = find(rawCode);
        synchronized (lobby) {
            activeGame(lobby, guestId);
            MultiplayerGameSnapshot result = games.ready(UUID.fromString(lobby.gameId), guestId);
            if (result.status().equals("FINISHED")) lobby.status = "FINISHED";
            publish(lobby, "GAME_UPDATED");
            return result;
        }
    }

    private MultiplayerGameSnapshot activeGame(Lobby lobby, UUID guestId) {
        ensureActive(lobby);
        requireMember(lobby, guestId);
        if (lobby.gameId == null) throw new LobbyException(LobbyError.GAME_NOT_AVAILABLE, "Game has not started");
        if (!lobby.currentGamePlayers.contains(guestId))
            throw new LobbyException(LobbyError.GAME_NOT_AVAILABLE, "Player was not in this game");
        MultiplayerGameSnapshot result = games.get(UUID.fromString(lobby.gameId), guestId);
        if (result.status().equals("FINISHED")) lobby.status = "FINISHED";
        return result;
    }

    private Lobby find(String rawCode) {
        String code = validCode(rawCode);
        Lobby lobby = lobbies.get(code);
        if (lobby == null) throw new LobbyException(LobbyError.LOBBY_NOT_FOUND, "Lobby was not found");
        return lobby;
    }

    private void ensureWaiting(Lobby lobby) {
        ensureActive(lobby);
        if (!"WAITING".equals(lobby.status))
            throw new LobbyException(LobbyError.GAME_IN_PROGRESS, "Lobby game is already in progress");
    }

    private void ensureJoinable(Lobby lobby) {
        ensureActive(lobby);
        if ("IN_GAME".equals(lobby.status))
            throw new LobbyException(LobbyError.GAME_IN_PROGRESS, "Lobby game is already in progress");
    }

    private void ensureActive(Lobby lobby) {
        if (!clock.instant().isBefore(lobby.expiresAt)) lobby.status = "EXPIRED";
        if ("EXPIRED".equals(lobby.status)) {
            throw new LobbyException(LobbyError.LOBBY_EXPIRED, "Lobby has expired");
        }
        if ("IN_GAME".equals(lobby.status) && lobby.gameId != null
                && games.isFinished(UUID.fromString(lobby.gameId))) {
            lobby.status = "FINISHED";
            publish(lobby, "GAME_FINISHED");
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpired() {
        Instant now = clock.instant();
        lobbies.forEach((code, lobby) -> {
            synchronized (lobby) {
                if (!now.isBefore(lobby.expiresAt) || lobby.members.isEmpty()) {
                    lobby.status = "EXPIRED";
                    if (lobbies.remove(code, lobby)) {
                        discardGame(lobby);
                        publish(lobby, "LOBBY_EXPIRED");
                    }
                }
            }
        });
    }

    private void discardGame(Lobby lobby) {
        if (lobby.gameId != null) games.discard(UUID.fromString(lobby.gameId));
    }

    private static Member requireMember(Lobby lobby, UUID guestId) {
        Member member = lobby.members.get(guestId);
        if (member == null) throw new LobbyException(LobbyError.NOT_MEMBER, "Guest is not in this lobby");
        return member;
    }

    private static String validName(String rawName) {
        if (rawName == null) throw new LobbyException(LobbyError.INVALID_DISPLAY_NAME, "Display name is required");
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(Character::isISOControl)) {
            throw new LobbyException(LobbyError.INVALID_DISPLAY_NAME, "Display name must contain 1 to 32 visible characters");
        }
        return name;
    }

    private static String validCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{6}")) {
            throw new LobbyException(LobbyError.INVALID_CODE, "Lobby code must contain 6 letters or digits");
        }
        return code;
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }

    private void publish(Lobby lobby, String type) {
        events.publishEvent(new LobbyEvent(lobby.code, type, ++lobby.sequence,
                clock.instant(), null, Map.of()));
    }

    private static final class Lobby {
        private final String code;
        private final int maxPlayers;
        private final Instant expiresAt;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Set<UUID> currentGamePlayers = new HashSet<>();
        private String status = "WAITING";
        private long sequence;
        private String gameId;

        private Lobby(String code, int maxPlayers, Instant expiresAt) {
            this.code = code;
            this.maxPlayers = maxPlayers;
            this.expiresAt = expiresAt;
        }

        private LobbySnapshot snapshot(UUID guestId) {
            List<LobbySnapshot.Participant> participants = new ArrayList<>();
            for (Member member : members.values()) {
                participants.add(new LobbySnapshot.Participant(member.name, member.role));
            }
            Member you = members.get(guestId);
            return new LobbySnapshot(code, status, maxPlayers, expiresAt,
                    you.role, you.name, sequence, List.copyOf(participants),
                    currentGamePlayers.contains(guestId) ? gameId : null);
        }
    }

    private static final class Member {
        private final String name;
        private String role;

        private Member(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }
}
