package com.project2.backend.game.session;

import com.project2.backend.common.GameConfig;
import com.project2.backend.game.color.ColorGameConfig;
import com.project2.backend.game.color.ColorGameDefinition;
import com.project2.backend.game.color.ColorGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SingleGameServiceTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final GameConfig config = new GameConfig("test-v1", 5, 1_000, 500, 2_000, 1_000);
    private final SingleGameService service = new SingleGameService(clock,
            List.of(new ColorGameDefinition(new ColorGameConfig(config, 20), new ColorGenerator())));

    @Test
    void inactiveGamesExpireButRecentlyReadGameRemains() {
        UUID inactive = service.start("COLOR_GUESS").id();
        UUID active = service.start("COLOR_GUESS").id();
        clock.advanceMillis(60 * 60 * 1000L);
        service.get(active);
        clock.advanceMillis(60 * 60 * 1000L + 1);
        service.cleanup();
        assertError("GAME_NOT_FOUND", () -> service.get(inactive));
        assertEquals(active, service.get(active).id());
    }

    @Test
    void hidesTargetUntilSubmissionThenWaitsForContinue() {
        SingleGameSnapshot started = service.start("COLOR_GUESS");
        UUID id = started.id();
        String target = target(started);
        assertEquals(GamePhase.PREVIEW, started.phase());
        assertEquals(clock.instant().plusMillis(1000), started.phaseEndsAt());

        clock.advanceMillis(1000);
        assertEquals(GamePhase.TRANSITION, service.get(id).phase());
        assertNull(service.get(id).gameData());
        clock.advanceMillis(500);
        SingleGameSnapshot input = service.get(id);
        assertEquals(GamePhase.INPUT, input.phase());
        assertNull(input.gameData());
        assertTrue(input.revealedRounds().isEmpty());

        SingleGameSnapshot submitted = service.submit(id, 1, guess(target));
        assertEquals(GamePhase.REVEAL, submitted.phase());
        assertEquals(target, ((ColorGameDefinition.ColorResult) submitted.gameData()).targetColor());
        assertEquals(10, submitted.totalScore(), 1e-12);
        assertNull(submitted.phaseEndsAt());

        clock.advanceMillis(20_000);
        SingleGameSnapshot reveal = service.get(id);
        assertEquals(GamePhase.REVEAL, reveal.phase());
        assertEquals(1, reveal.currentRound());
        assertEquals(target, ((ColorGameDefinition.ColorResult) reveal.gameData()).targetColor());
        assertEquals(10, reveal.totalScore(), 1e-12);
        assertEquals(10, reveal.revealedRounds().getFirst().score(), 1e-12);
        assertEquals(0, reveal.revealedRounds().getFirst().responseTimeMs());
        SingleGameSnapshot next = service.continueGame(id);
        assertEquals(2, next.currentRound());
        assertEquals(GamePhase.PREVIEW, next.phase());
    }

    @Test
    void rejectsEarlyLateAndDifferentSecondGuessesButAllowsIdenticalRetry() {
        SingleGameSnapshot started = service.start("COLOR_GUESS");
        UUID id = started.id();
        assertError("SUBMISSION_WINDOW_CLOSED", () -> service.submit(id, 1, guess("#112233")));
        assertError("ROUND_NOT_READY", () -> service.continueGame(id));

        clock.advanceMillis(1500);
        assertError("ROUND_NOT_CURRENT", () -> service.submit(id, 2, guess("#112233")));
        service.submit(id, 1, guess("#112233"));
        assertEquals(GamePhase.REVEAL, service.submit(id, 1, guess("#112233")).phase());
        assertError("SUBMISSION_CONFLICT", () -> service.submit(id, 1, guess("#445566")));

        clock.advanceMillis(2000);
        assertEquals(GamePhase.REVEAL, service.submit(id, 1, guess("#112233")).phase());
        assertError("INVALID_GUESS", () -> service.submit(id, 1, guess("red")));

        clock.advanceMillis(1000);
        assertEquals(1, service.get(id).currentRound());
        service.continueGame(id);
        assertEquals(2, service.get(id).currentRound());
        assertError("SUBMISSION_WINDOW_CLOSED", () -> service.submit(id, 2, guess("#112233")));
        clock.advanceMillis(3500);
        assertError("SUBMISSION_WINDOW_CLOSED", () -> service.submit(id, 2, guess("#112233")));
    }

    @Test
    void completesFiveRoundsWithServerCalculatedMaximumScore() {
        SingleGameSnapshot snapshot = service.start("COLOR_GUESS");
        UUID id = snapshot.id();
        for (int round = 1; round <= 5; round++) {
            assertEquals(round, snapshot.currentRound());
            assertEquals(GamePhase.PREVIEW, snapshot.phase());
            String target = target(snapshot);
            clock.advanceMillis(1500);
            snapshot = service.submit(id, round, guess(target));
            assertEquals(GamePhase.REVEAL, snapshot.phase());
            assertEquals(round * 10.0, snapshot.totalScore(), 1e-10);
            assertEquals(round, snapshot.revealedRounds().size());
            snapshot = service.continueGame(id);
        }
        assertEquals(GameStatus.FINISHED, snapshot.status());
        assertEquals(GamePhase.COMPLETED, snapshot.phase());
        assertEquals(5, snapshot.currentRound());
        assertEquals(50, snapshot.totalScore(), 1e-10);
        assertEquals(5, snapshot.revealedRounds().size());
        assertNull(snapshot.phaseEndsAt());
        String lastGuess = ((ColorGameDefinition.ColorResult) snapshot.revealedRounds().getLast().gameData()).guessedColor();
        String otherGuess = lastGuess.equals("#000000")
                ? "#FFFFFF" : "#000000";
        assertError("SUBMISSION_CONFLICT", () -> service.submit(id, 5, guess(otherGuess)));
        assertError("GAME_FINISHED", () -> service.continueGame(id));
    }

    @Test
    void missedRoundsBecomeZeroAndUnknownGameReturnsNotFound() {
        SingleGameSnapshot started = service.start("COLOR_GUESS");
        clock.advanceMillis(22_500);
        SingleGameSnapshot first = service.get(started.id());
        assertEquals(GamePhase.REVEAL, first.phase());
        assertEquals(1, first.currentRound());
        for (int round = 1; round <= 5; round++) {
            SingleGameSnapshot next = service.continueGame(started.id());
            if (round < 5) {
                assertEquals(GamePhase.PREVIEW, next.phase());
                clock.advanceMillis(3_500);
            }
        }
        SingleGameSnapshot finished = service.get(started.id());
        assertEquals(GameStatus.FINISHED, finished.status());
        assertEquals(5, finished.revealedRounds().size());
        assertEquals(0, finished.totalScore());
        assertTrue(finished.revealedRounds().stream().allMatch(round -> {
            ColorGameDefinition.ColorResult result = (ColorGameDefinition.ColorResult) round.gameData();
            return result.guessedColor() == null && result.colorDistance() == null && round.score() == 0;
        }));
        GameException missing = assertThrows(GameException.class, () -> service.get(UUID.randomUUID()));
        assertEquals("GAME_NOT_FOUND", missing.code());
    }

    @Test
    void latestDraftIsScoredAtTimeoutUnlessPlayerSubmits() {
        SingleGameSnapshot started = service.start("COLOR_GUESS");
        String target = target(started);
        clock.advanceMillis(1000);
        service.saveDraft(started.id(), 1, guess("#000000"), 1);
        service.saveDraft(started.id(), 1, guess(target), 2);
        service.saveDraft(started.id(), 1, guess("#FFFFFF"), 1);
        assertEquals(target, service.get(started.id()).yourDraft());
        clock.advanceMillis(2500);
        SingleGameSnapshot result = service.get(started.id());
        assertEquals(GamePhase.REVEAL, result.phase());
        assertEquals(10, result.totalScore(), 1e-12);
        assertEquals(target, ((ColorGameDefinition.ColorResult) result.gameData()).guessedColor());

        SingleGameSnapshot other = service.start("COLOR_GUESS");
        clock.advanceMillis(1500);
        service.saveDraft(other.id(), 1, guess("#000000"), 1);
        SingleGameSnapshot submitted = service.submit(other.id(), 1, guess("#FFFFFF"));
        assertEquals("#FFFFFF", ((ColorGameDefinition.ColorResult) submitted.gameData()).guessedColor());
    }

    @Test
    void sharedSessionRunsAnotherGameWithoutColorRules() {
        SingleGameService multiGameService = new SingleGameService(clock, List.of(
                new ColorGameDefinition(new ColorGameConfig(config, 20), new ColorGenerator()),
                new NumberGameDefinition()));
        SingleGameSnapshot started = multiGameService.start("NUMBER_MEMORY");
        assertEquals("NUMBER_MEMORY", started.gameType());
        assertEquals(new NumberPreview(7), started.gameData());
        clock.advanceMillis(1500);
        assertNull(multiGameService.get(started.id()).gameData());
        SingleGameSnapshot revealed = multiGameService.submit(started.id(), 1, Map.of("number", 7));
        assertEquals(10, revealed.totalScore());
        assertEquals(new NumberResult(7, 7), revealed.gameData());
    }

    private void assertError(String code, Runnable action) {
        GameException error = assertThrows(GameException.class, action::run);
        assertEquals(code, error.code());
    }

    private static String target(SingleGameSnapshot snapshot) {
        return ((ColorGameDefinition.ColorPreview) snapshot.gameData()).targetColor();
    }

    private static Map<String, Object> guess(String color) {
        return Map.of("color", color);
    }

    private record NumberPreview(int target) implements GameData {
    }

    private record NumberResult(int target, Integer guess) implements GameData {
    }

    private static final class NumberGameDefinition implements GameDefinition<Integer, Integer> {
        @Override public String gameType() { return "NUMBER_MEMORY"; }
        @Override public GameConfig config() { return new GameConfig("number-v1", 1, 1000, 500, 2000, 1000); }
        @Override public Integer createTarget() { return 7; }
        @Override public Integer parseGuess(Map<String, Object> input) {
            if (input == null || !(input.get("number") instanceof Integer number)) {
                throw new IllegalArgumentException("guess.number must be an integer");
            }
            return number;
        }
        @Override public String guessKey(Integer guess) { return guess.toString(); }
        @Override public GameData preview(Integer target) { return new NumberPreview(target); }
        @Override public GameEvaluation evaluate(Integer target, Integer guess) {
            return new GameEvaluation(target.equals(guess) ? 10 : 0, new NumberResult(target, guess));
        }
        @Override public GameData missedResult(Integer target) { return new NumberResult(target, null); }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
