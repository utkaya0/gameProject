package com.project2.backend.game.session;

import com.project2.backend.common.GameConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class SingleGameService {
    private static final Duration GAME_LIFETIME = Duration.ofHours(2);
    private final Map<UUID, RunningGame<?, ?>> games = new ConcurrentHashMap<>();
    private final Map<String, GameDefinition<?, ?>> definitions;
    private final Clock clock;

    @Autowired
    public SingleGameService(List<GameDefinition<?, ?>> definitions) {
        this(Clock.systemUTC(), definitions);
    }

    SingleGameService(Clock clock, List<GameDefinition<?, ?>> definitions) {
        this.clock = clock;
        Map<String, GameDefinition<?, ?>> byType = new HashMap<>();
        for (GameDefinition<?, ?> definition : definitions) {
            if (byType.putIfAbsent(definition.gameType(), definition) != null) {
                throw new IllegalArgumentException("Duplicate game type: " + definition.gameType());
            }
        }
        this.definitions = Map.copyOf(byType);
    }

    public SingleGameSnapshot start(String gameType) {
        GameDefinition<?, ?> definition = definitions.get(gameType);
        if (definition == null) {
            throw new GameException(GameError.UNKNOWN_GAME_TYPE, "Unknown game type");
        }
        Instant now = clock.instant();
        RunningGame<?, ?> game = createGame(definition, now);
        games.put(game.id, game);
        return game.snapshot(now);
    }

    public SingleGameSnapshot get(UUID id) {
        RunningGame<?, ?> game = find(id);
        synchronized (game) {
            Instant now = clock.instant();
            return game.snapshot(now);
        }
    }

    public SingleGameSnapshot submit(UUID id, int roundNumber, Map<String, Object> guess) {
        RunningGame<?, ?> game = find(id);
        synchronized (game) {
            Instant now = clock.instant();
            return game.submit(roundNumber, guess, now);
        }
    }

    public SingleGameSnapshot saveDraft(UUID id, int roundNumber, Map<String, Object> guess, long revision) {
        RunningGame<?, ?> game = find(id);
        synchronized (game) {
            return game.saveDraft(roundNumber, guess, revision, clock.instant());
        }
    }

    public SingleGameSnapshot continueGame(UUID id) {
        RunningGame<?, ?> game = find(id);
        synchronized (game) {
            Instant now = clock.instant();
            return game.continueGame(now);
        }
    }

    private RunningGame<?, ?> find(UUID id) {
        RunningGame<?, ?> game = games.get(id);
        if (game == null) {
            throw new GameException(GameError.GAME_NOT_FOUND, "Game was not found");
        }
        game.lastTouched = clock.instant();
        return game;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant cutoff = clock.instant().minus(GAME_LIFETIME);
        games.entrySet().removeIf(entry -> entry.getValue().lastTouched.isBefore(cutoff));
    }

    private <Target, Guess> RunningGame<Target, Guess> createGame(
            GameDefinition<Target, Guess> definition, Instant now) {
        return new RunningGame<>(UUID.randomUUID(), definition, now);
    }

    private static final class RunningGame<Target, Guess> {
        private final UUID id;
        private final GameDefinition<Target, Guess> definition;
        private final GameConfig config;
        private final List<RoundState<Target>> rounds = new ArrayList<>();
        private boolean finished;
        private volatile Instant lastTouched;

        private RunningGame(UUID id, GameDefinition<Target, Guess> definition, Instant startedAt) {
            this.id = id;
            this.definition = definition;
            this.config = definition.config();
            this.lastTouched = startedAt;
            rounds.add(new RoundState<>(1, startedAt, config, definition.createTarget()));
        }

        private RoundState<Target> current() {
            return rounds.get(rounds.size() - 1);
        }

        private SingleGameSnapshot continueGame(Instant now) {
            if (finished) {
                throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            }
            if (phaseAt(now) != GamePhase.REVEAL) {
                throw new GameException(GameError.ROUND_NOT_READY, "Round result is not ready");
            }
            if (rounds.size() == config.totalRounds()) {
                finished = true;
            } else {
                rounds.add(new RoundState<>(rounds.size() + 1, now, config,
                        definition.createTarget()));
            }
            return snapshot(now);
        }

        private GamePhase phaseAt(Instant now) {
            if (finished) return GamePhase.COMPLETED;
            RoundState<Target> round = current();
            if (round.guessKey == null && round.timeline.phaseAt(now) == GamePhase.REVEAL
                    && round.draftEvaluation != null) {
                round.guessKey = round.draftKey;
                round.evaluation = round.draftEvaluation;
            }
            return round.guessKey != null ? GamePhase.REVEAL : round.timeline.phaseAt(now);
        }

        private SingleGameSnapshot saveDraft(int roundNumber, Map<String, Object> input,
                                             long revision, Instant now) {
            if (revision < 0) throw new GameException(GameError.INVALID_GUESS, "Draft revision is invalid");
            final Guess guess;
            try {
                guess = definition.parseGuess(input);
            } catch (IllegalArgumentException exception) {
                throw new GameException(GameError.INVALID_GUESS, exception.getMessage());
            }
            if (finished) throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            if (roundNumber != rounds.size())
                throw new GameException(GameError.ROUND_NOT_CURRENT, "Round is not current");
            RoundState<Target> round = current();
            if (round.guessKey != null) return snapshot(now);
            GamePhase phase = phaseAt(now);
            if (phase != GamePhase.TRANSITION && phase != GamePhase.INPUT)
                throw new GameException(GameError.SUBMISSION_WINDOW_CLOSED, "Draft window is closed");
            if (revision > round.draftRevision) {
                round.draftRevision = revision;
                round.draftKey = definition.guessKey(guess);
                round.draftEvaluation = definition.evaluate(round.target, guess);
            }
            return snapshot(now);
        }

        private SingleGameSnapshot submit(int roundNumber, Map<String, Object> input, Instant now) {
            phaseAt(now);
            final Guess guess;
            final String guessKey;
            try {
                guess = definition.parseGuess(input);
                guessKey = definition.guessKey(guess);
            } catch (IllegalArgumentException exception) {
                throw new GameException(GameError.INVALID_GUESS, exception.getMessage());
            }

            if (roundNumber > 0 && roundNumber <= rounds.size()) {
                RoundState<Target> existing = rounds.get(roundNumber - 1);
                if (existing.guessKey != null) {
                    if (existing.guessKey.equals(guessKey)) return snapshot(now);
                    throw new GameException(GameError.SUBMISSION_CONFLICT,
                            "This round already has a different submission");
                }
            }
            if (finished) {
                throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            }
            if (roundNumber != rounds.size()) {
                throw new GameException(GameError.ROUND_NOT_CURRENT, "Round is not current");
            }

            RoundState<Target> round = current();
            if (round.timeline.phaseAt(now) != GamePhase.INPUT) {
                throw new GameException(GameError.SUBMISSION_WINDOW_CLOSED,
                        "Submission is accepted only during INPUT");
            }
            GameEvaluation evaluation = definition.evaluate(round.target, guess);
            round.guessKey = guessKey;
            round.evaluation = evaluation;
            round.responseTimeMs = Duration.between(round.timeline.inputOpensAt(), now).toMillis();
            return snapshot(now);
        }

        private SingleGameSnapshot snapshot(Instant now) {
            RoundState<Target> current = current();
            GamePhase phase = phaseAt(now);
            GameData visibleData = switch (phase) {
                case PREVIEW -> definition.preview(current.target);
                case REVEAL -> resultData(current);
                default -> null;
            };
            List<SingleGameSnapshot.RoundResult> revealed = new ArrayList<>();
            double totalScore = 0;
            int visibleCount = rounds.size() - (phase == GamePhase.REVEAL || phase == GamePhase.COMPLETED ? 0 : 1);
            for (int i = 0; i < visibleCount; i++) {
                RoundState<Target> round = rounds.get(i);
                double score = round.evaluation == null ? 0 : round.evaluation.score();
                revealed.add(new SingleGameSnapshot.RoundResult(round.number, score,
                        round.responseTimeMs, resultData(round)));
                totalScore += score;
            }
            return new SingleGameSnapshot(id, definition.gameType(), "SINGLE_PLAYER",
                    finished ? GameStatus.FINISHED : GameStatus.IN_PROGRESS,
                    config.version(), current.number, config.totalRounds(), phase, now,
                    phase == GamePhase.REVEAL ? null : current.timeline.endOf(phase),
                    visibleData, current.draftKey, totalScore, List.copyOf(revealed));
        }

        private GameData resultData(RoundState<Target> round) {
            return round.evaluation == null ? definition.missedResult(round.target) : round.evaluation.result();
        }
    }

    private static final class RoundState<Target> {
        private final int number;
        private final Target target;
        private final RoundTimeline timeline;
        private String guessKey;
        private GameEvaluation evaluation;
        private Long responseTimeMs;
        private long draftRevision = -1;
        private String draftKey;
        private GameEvaluation draftEvaluation;

        private RoundState(int number, Instant startedAt, GameConfig config, Target target) {
            this.number = number;
            this.target = target;
            this.timeline = RoundTimeline.startingAt(startedAt, config);
        }
    }
}
