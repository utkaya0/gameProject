package com.project2.backend.lobby;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class LobbyServiceTests {
    private final LobbyService service = new LobbyService(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsJoinsAndTransfersHostWhenHostLeaves() {
        UUID host = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LobbySnapshot created = service.create(host, "  Ada  ", 3);
        assertEquals(6, created.code().length());
        assertEquals("HOST", created.yourRole());
        assertEquals("Ada", created.yourDisplayName());

        LobbySnapshot joined = service.join(player, created.code().toLowerCase(), "Bora");
        assertEquals("PLAYER", joined.yourRole());
        assertEquals(2, joined.participants().size());
        assertEquals(2, service.join(player, created.code(), "Bora").participants().size());
        service.leave(host, created.code());
        assertEquals("HOST", service.get(player, created.code()).yourRole());
        service.leave(player, created.code());
        assertError(LobbyError.LOBBY_NOT_FOUND, () -> service.get(player, created.code()));
    }

    @Test
    void rejectsDuplicateNamesFullLobbyAndUnauthorizedStart() {
        UUID host = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LobbySnapshot created = service.create(host, "Ada", 2);
        assertError(LobbyError.DISPLAY_NAME_TAKEN,
                () -> service.join(player, created.code(), "ada"));
        service.join(player, created.code(), "Bora");
        assertError(LobbyError.LOBBY_FULL,
                () -> service.join(UUID.randomUUID(), created.code(), "Cem"));
        assertError(LobbyError.NOT_HOST,
                () -> service.assertHostForStart(player, created.code()));
        assertDoesNotThrow(() -> service.assertHostForStart(host, created.code()));
        assertEquals("IN_PROGRESS", service.start(host, created.code()).status());
        assertEquals("IN_GAME", service.get(host, created.code()).status());
        assertError(LobbyError.GAME_IN_PROGRESS, () -> service.start(host, created.code()));
        assertError(LobbyError.NOT_MEMBER,
                () -> service.get(UUID.randomUUID(), created.code()));
        assertError(LobbyError.INVALID_CODE,
                () -> service.join(UUID.randomUUID(), "ABC", "Cem"));
    }

    @Test
    void onlyOnePlayerTakesLastSeatUnderConcurrentJoins() throws Exception {
        LobbySnapshot created = service.create(UUID.randomUUID(), "Ada", 2);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> tryJoin(release, created.code(), "Bora"));
            Future<Boolean> second = executor.submit(() -> tryJoin(release, created.code(), "Cem"));
            release.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
    }

    @Test
    void simultaneousStartsCreateOnlyOneGame() throws Exception {
        UUID host = UUID.randomUUID();
        LobbySnapshot lobby = service.create(host, "Ada", 2);
        service.join(UUID.randomUUID(), lobby.code(), "Bora");
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> tryStart(release, host, lobby.code()));
            Future<Boolean> second = executor.submit(() -> tryStart(release, host, lobby.code()));
            release.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertNotNull(service.get(host, lobby.code()).gameId());
    }

    @Test
    void leavingAnActiveGameRemovesPlayerAndTransfersHost() {
        UUID host = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LobbySnapshot lobby = service.create(host, "Ada", 2);
        service.join(player, lobby.code(), "Bora");
        service.start(host, lobby.code());
        service.leave(host, lobby.code());
        assertEquals("HOST", service.get(player, lobby.code()).yourRole());
        assertEquals(1, service.game(player, lobby.code()).players().size());
        assertError(LobbyError.NOT_MEMBER, () -> service.game(host, lobby.code()));
    }

    @Test
    void finishedLobbyAcceptsNewPlayerAndStartsFreshRematch() {
        MutableClock clock = new MutableClock();
        LobbyService lobbies = new LobbyService(clock);
        UUID host = UUID.randomUUID();
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        LobbySnapshot lobby = lobbies.create(host, "Ada", 2);
        lobbies.join(oldPlayer, lobby.code(), "Bora");
        UUID firstGameId = lobbies.start(host, lobby.code()).id();
        for (int round = 1; round <= 5; round++) {
            clock.advanceMillis(13_750);
            var result = lobbies.game(host, lobby.code());
            assertEquals("REVEAL", result.phase().name());
            assertEquals(0.0, result.players().get(0).roundScore());
            lobbies.ready(host, lobby.code());
            lobbies.ready(oldPlayer, lobby.code());
        }
        assertEquals("FINISHED", lobbies.get(host, lobby.code()).status());
        lobbies.leave(oldPlayer, lobby.code());
        assertError(LobbyError.NOT_ENOUGH_PLAYERS, () -> lobbies.rematch(host, lobby.code()));
        LobbySnapshot joined = lobbies.join(newPlayer, lobby.code(), "Cem");
        assertNull(joined.gameId());
        assertError(LobbyError.GAME_NOT_AVAILABLE, () -> lobbies.game(newPlayer, lobby.code()));
        assertError(LobbyError.NOT_HOST, () -> lobbies.rematch(newPlayer, lobby.code()));
        var rematch = lobbies.rematch(host, lobby.code());
        assertNotEquals(firstGameId, rematch.id());
        assertEquals(1, rematch.currentRound());
        assertEquals(2, rematch.players().size());
        assertEquals("IN_GAME", lobbies.get(newPlayer, lobby.code()).status());
        assertEquals(rematch.id().toString(), lobbies.get(newPlayer, lobby.code()).gameId());
        assertError(LobbyError.GAME_IN_PROGRESS, () -> lobbies.rematch(host, lobby.code()));
    }

    @Test
    void expiredLobbyAndItsGameAreCleanedUp() {
        MutableClock clock = new MutableClock();
        LobbyService lobbies = new LobbyService(clock);
        UUID host = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LobbySnapshot lobby = lobbies.create(host, "Ada", 2);
        lobbies.join(player, lobby.code(), "Bora");
        lobbies.start(host, lobby.code());
        clock.advanceMillis(2 * 60 * 60 * 1000L + 1);
        lobbies.cleanupExpired();
        assertError(LobbyError.LOBBY_NOT_FOUND, () -> lobbies.get(host, lobby.code()));
    }

    private boolean tryStart(CountDownLatch release, UUID host, String code) throws InterruptedException {
        release.await();
        try {
            service.start(host, code);
            return true;
        } catch (LobbyException exception) {
            assertEquals(LobbyError.GAME_IN_PROGRESS, exception.error());
            return false;
        }
    }

    @Test
    void publishesOrderedJoinAndLeaveEvents() {
        List<LobbyEvent> events = new ArrayList<>();
        LobbyService liveService = new LobbyService(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                event -> events.add((LobbyEvent) event));
        UUID host = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LobbySnapshot created = liveService.create(host, "Ada", 2);
        assertEquals(0, created.sequence());
        LobbySnapshot joined = liveService.join(player, created.code(), "Bora");
        assertEquals(1, joined.sequence());
        liveService.leave(player, created.code());
        assertEquals(2, liveService.get(host, created.code()).sequence());
        assertEquals(List.of("PLAYER_JOINED", "PLAYER_LEFT"),
                events.stream().map(LobbyEvent::type).toList());
        assertEquals(List.of(1L, 2L), events.stream().map(LobbyEvent::sequence).toList());
    }

    private boolean tryJoin(CountDownLatch release, String code, String name) throws InterruptedException {
        release.await();
        try {
            service.join(UUID.randomUUID(), code, name);
            return true;
        } catch (LobbyException exception) {
            assertEquals(LobbyError.LOBBY_FULL, exception.error());
            return false;
        }
    }

    private void assertError(LobbyError expected, Runnable action) {
        LobbyException exception = assertThrows(LobbyException.class, action::run);
        assertEquals(expected, exception.error());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advanceMillis(long millis) { now = now.plusMillis(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
