package com.project2.backend.game.multiplayer;

import com.project2.backend.game.color.ColorGameDefinition;
import com.project2.backend.game.session.GameError;
import com.project2.backend.game.session.GameException;
import com.project2.backend.game.session.GamePhase;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerGameServiceTests {
    private final MutableClock clock = new MutableClock();
    private final MultiplayerGameService service = new MultiplayerGameService(clock, List.of(new ColorGameDefinition()));
    private final UUID ada = UUID.randomUUID();
    private final UUID bora = UUID.randomUUID();

    @Test
    void fiveRoundsShareOneTimelineAndFinishWithSameScores() {
        Map<UUID, String> roster = new LinkedHashMap<>();
        roster.put(ada, "Ada"); roster.put(bora, "Bora");
        var started = service.start("ABC123", roster, ada);
        UUID id = started.id();
        assertEquals(started.gameData(), service.get(id, bora).gameData());
        for (int number = 1; number <= 5; number++) {
            int round = number;
            clock.advanceMillis(3750);
            var input = service.get(id, ada);
            assertEquals(GamePhase.INPUT, input.phase());
            assertNull(input.gameData());
            var adaGuess = service.submit(id, ada, number, Map.of("color", "#808080"));
            assertEquals(GamePhase.INPUT, adaGuess.phase());
            assertNull(adaGuess.players().get(1).roundScore());
            assertFalse(service.get(id, bora).yourSubmissionReceived());
            GameException conflict = assertThrows(GameException.class,
                    () -> service.submit(id, ada, round, Map.of("color", "#FFFFFF")));
            assertEquals(GameError.SUBMISSION_CONFLICT, conflict.error());
            var reveal = service.submit(id, bora, number, Map.of("color", "#808080"));
            assertEquals(GamePhase.REVEAL, reveal.phase());
            assertEquals(reveal.players().get(0).roundScore(), reveal.players().get(1).roundScore());
            assertEquals(number, reveal.revealedRounds().size());
            service.ready(id, ada);
            assertEquals(GamePhase.REVEAL, service.get(id, bora).phase());
            clock.advanceMillis(15_001);
            var advanced = service.get(id, bora);
            assertEquals(number == 5 ? GamePhase.COMPLETED : GamePhase.PREVIEW, advanced.phase());
        }
        var finalA = service.get(id, ada);
        var finalB = service.get(id, bora);
        assertEquals("FINISHED", finalA.status());
        assertEquals(finalA.players(), finalB.players());
        assertEquals(finalA.revealedRounds(), finalB.revealedRounds());
    }

    @Test
    void allReadyAdvancesImmediatelyAndLateGuessIsRejected() {
        Map<UUID, String> roster = Map.of(ada, "Ada", bora, "Bora");
        UUID id = service.start("ABC123", roster, ada).id();
        clock.advanceMillis(13_750);
        assertEquals(GamePhase.REVEAL, service.get(id, ada).phase());
        GameException late = assertThrows(GameException.class,
                () -> service.submit(id, ada, 1, Map.of("color", "#808080")));
        assertEquals(GameError.SUBMISSION_WINDOW_CLOSED, late.error());
        service.ready(id, ada);
        assertEquals(GamePhase.PREVIEW, service.ready(id, bora).phase());
    }

    @Test
    void remainingPlayerDoesNotWaitForPlayerWhoLeft() {
        Map<UUID, String> roster = new LinkedHashMap<>();
        roster.put(ada, "Ada"); roster.put(bora, "Bora");
        UUID id = service.start("ABC123", roster, ada).id();
        clock.advanceMillis(3750);
        service.submit(id, ada, 1, Map.of("color", "#808080"));
        assertFalse(service.leave(id, bora));
        var remaining = service.get(id, ada);
        assertEquals(1, remaining.players().size());
        assertEquals(GamePhase.REVEAL, remaining.phase());
        assertEquals(GamePhase.PREVIEW, service.ready(id, ada).phase());
    }

    @Test
    void timeoutScoresEachPlayersLatestDraft() {
        Map<UUID, String> roster = new LinkedHashMap<>();
        roster.put(ada, "Ada"); roster.put(bora, "Bora");
        var started = service.start("ABC123", roster, ada);
        String target = ((ColorGameDefinition.ColorPreview) started.gameData()).targetColor();
        clock.advanceMillis(3000);
        service.saveDraft(started.id(), ada, 1, Map.of("color", "#000000"), 1);
        service.saveDraft(started.id(), ada, 1, Map.of("color", target), 2);
        service.saveDraft(started.id(), ada, 1, Map.of("color", "#FFFFFF"), 1);
        service.saveDraft(started.id(), bora, 1, Map.of("color", target), 1);
        assertEquals(target, service.get(started.id(), ada).yourDraft());
        assertEquals(target, service.get(started.id(), bora).yourDraft());
        clock.advanceMillis(750);
        assertEquals(GamePhase.INPUT, service.get(started.id(), ada).phase());
        assertNull(service.get(started.id(), ada).gameData());
        clock.advanceMillis(10_000);
        var result = service.get(started.id(), ada);
        assertEquals(GamePhase.REVEAL, result.phase());
        assertEquals(10.0, result.players().get(0).roundScore());
        assertEquals(10.0, result.players().get(1).roundScore());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advanceMillis(long millis) { now = now.plusMillis(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
