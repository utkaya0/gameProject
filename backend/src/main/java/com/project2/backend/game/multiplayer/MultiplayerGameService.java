package com.project2.backend.game.multiplayer;

import com.project2.backend.common.GameConfig;
import com.project2.backend.game.session.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class MultiplayerGameService {
    private final Clock clock;
    private final GameDefinition<?, ?> definition;
    private final Map<UUID, Match<?>> matches = new ConcurrentHashMap<>();

    @Autowired
    public MultiplayerGameService(List<GameDefinition<?, ?>> definitions) {
        this(Clock.systemUTC(), definitions);
    }

    public MultiplayerGameService(Clock clock, List<GameDefinition<?, ?>> definitions) {
        this.clock = clock;
        this.definition = definitions.stream().filter(item -> item.gameType().equals("COLOR_GUESS"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("COLOR_GUESS definition is required"));
    }

    public MultiplayerGameSnapshot start(String code, Map<UUID, String> players, UUID viewer) {
        Match<?> match = create(code, players, clock.instant());
        matches.put(match.id, match);
        return match.snapshot(clock.instant(), viewer);
    }

    private <T, G> Match<T> create(String code, Map<UUID, String> players, Instant now) {
        @SuppressWarnings("unchecked") GameDefinition<T, G> typed = (GameDefinition<T, G>) definition;
        return new Match<>(code, players, typed, now);
    }

    public MultiplayerGameSnapshot get(UUID id, UUID viewer) {
        Match<?> match = find(id);
        synchronized (match) { return match.snapshot(clock.instant(), viewer); }
    }

    public MultiplayerGameSnapshot submit(UUID id, UUID viewer, int round, Map<String, Object> guess) {
        Match<?> match = find(id);
        synchronized (match) { return match.submit(viewer, round, guess, clock.instant()); }
    }

    public MultiplayerGameSnapshot saveDraft(UUID id, UUID viewer, int round,
                                             Map<String, Object> guess, long revision) {
        Match<?> match = find(id);
        synchronized (match) { return match.saveDraft(viewer, round, guess, revision, clock.instant()); }
    }

    public MultiplayerGameSnapshot ready(UUID id, UUID viewer) {
        Match<?> match = find(id);
        synchronized (match) { return match.ready(viewer, clock.instant()); }
    }

    public boolean leave(UUID id, UUID viewer) {
        Match<?> match = find(id);
        synchronized (match) { return match.leave(viewer, clock.instant()); }
    }

    public boolean isFinished(UUID id) {
        Match<?> match = find(id);
        synchronized (match) {
            match.advance(clock.instant());
            return match.finished;
        }
    }

    public void discard(UUID id) {
        matches.remove(id);
    }

    private Match<?> find(UUID id) {
        Match<?> match = matches.get(id);
        if (match == null) throw new GameException(GameError.GAME_NOT_FOUND, "Game was not found");
        return match;
    }

    private static final class Match<T> {
        private final UUID id = UUID.randomUUID();
        private final String code;
        private final GameDefinition<T, ?> definition;
        private final GameConfig config;
        private final LinkedHashMap<UUID, String> players;
        private final List<Round<T>> rounds = new ArrayList<>();
        private boolean finished;

        Match(String code, Map<UUID, String> players, GameDefinition<T, ?> definition, Instant now) {
            this.code = code;
            this.definition = definition;
            this.config = definition.config();
            this.players = new LinkedHashMap<>(players);
            rounds.add(new Round<>(1, now, definition.createTarget(), config));
        }

        private Round<T> current() { return rounds.get(rounds.size() - 1); }

        private void advance(Instant now) {
            if (finished) return;
            Round<T> round = current();
            if (round.timeline.phaseAt(now) == GamePhase.REVEAL) {
                for (UUID player : players.keySet()) {
                    Draft draft = round.drafts.get(player);
                    if (!round.guesses.containsKey(player) && draft != null) {
                        round.guesses.put(player, draft.key());
                        round.evaluations.put(player, draft.evaluation());
                    }
                }
            }
            if (round.readyDeadline != null && (!now.isBefore(round.readyDeadline) || round.ready.size() == players.size())) {
                if (round.number == config.totalRounds()) finished = true;
                else rounds.add(new Round<>(round.number + 1, now, definition.createTarget(), config));
            }
        }

        private GamePhase phase(Instant now) {
            if (finished) return GamePhase.COMPLETED;
            Round<T> round = current();
            if (round.timeline.phaseAt(now) == GamePhase.INPUT && round.guesses.size() == players.size()) return GamePhase.REVEAL;
            return round.timeline.phaseAt(now);
        }

        private MultiplayerGameSnapshot submit(UUID viewer, int number, Map<String, Object> input, Instant now) {
            advance(now);
            requirePlayer(viewer);
            final Object guess;
            final String key;
            try {
                guess = definition.parseGuess(input);
                key = guessKey(guess);
            } catch (IllegalArgumentException ex) {
                throw new GameException(GameError.INVALID_GUESS, ex.getMessage());
            }
            if (number > 0 && number <= rounds.size()) {
                String previous = rounds.get(number - 1).guesses.get(viewer);
                if (previous != null) {
                    if (previous.equals(key)) return snapshot(now, viewer);
                    throw new GameException(GameError.SUBMISSION_CONFLICT, "This round already has a different submission");
                }
            }
            if (finished) throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            if (number != current().number) throw new GameException(GameError.ROUND_NOT_CURRENT, "Round is not current");
            if (phase(now) != GamePhase.INPUT) throw new GameException(GameError.SUBMISSION_WINDOW_CLOSED, "Submission window is closed");
            Round<T> round = current();
            round.guesses.put(viewer, key);
            round.evaluations.put(viewer, evaluate(round.target, guess));
            return snapshot(now, viewer);
        }

        private MultiplayerGameSnapshot saveDraft(UUID viewer, int number, Map<String, Object> input,
                                                  long revision, Instant now) {
            advance(now);
            requirePlayer(viewer);
            if (revision < 0) throw new GameException(GameError.INVALID_GUESS, "Draft revision is invalid");
            final Object guess;
            final String key;
            try {
                guess = definition.parseGuess(input);
                key = guessKey(guess);
            } catch (IllegalArgumentException ex) {
                throw new GameException(GameError.INVALID_GUESS, ex.getMessage());
            }
            if (finished) throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            if (number != current().number) throw new GameException(GameError.ROUND_NOT_CURRENT, "Round is not current");
            Round<T> round = current();
            if (round.guesses.containsKey(viewer)) return snapshot(now, viewer);
            GamePhase phase = phase(now);
            if (phase != GamePhase.TRANSITION && phase != GamePhase.INPUT)
                throw new GameException(GameError.SUBMISSION_WINDOW_CLOSED, "Draft window is closed");
            Draft previous = round.drafts.get(viewer);
            if (previous == null || revision > previous.revision())
                round.drafts.put(viewer, new Draft(revision, key, evaluate(round.target, guess)));
            return snapshot(now, viewer);
        }

        @SuppressWarnings("unchecked")
        private <G> String guessKey(Object guess) { return ((GameDefinition<T, G>) definition).guessKey((G) guess); }

        @SuppressWarnings("unchecked")
        private <G> GameEvaluation evaluate(T target, Object guess) { return ((GameDefinition<T, G>) definition).evaluate(target, (G) guess); }

        private MultiplayerGameSnapshot ready(UUID viewer, Instant now) {
            advance(now);
            requirePlayer(viewer);
            if (finished) throw new GameException(GameError.GAME_FINISHED, "Game has finished");
            if (phase(now) != GamePhase.REVEAL) throw new GameException(GameError.ROUND_NOT_READY, "Round result is not ready");
            Round<T> round = current();
            if (round.ready.add(viewer) && round.readyDeadline == null) round.readyDeadline = now.plusSeconds(15);
            advance(now);
            return snapshot(now, viewer);
        }

        private void requirePlayer(UUID viewer) {
            if (!players.containsKey(viewer)) throw new IllegalArgumentException("Player is not in game");
        }

        private boolean leave(UUID viewer, Instant now) {
            requirePlayer(viewer);
            advance(now);
            if (finished) return true;
            players.remove(viewer);
            for (Round<T> round : rounds) {
                round.guesses.remove(viewer);
                round.evaluations.remove(viewer);
                round.ready.remove(viewer);
                round.drafts.remove(viewer);
            }
            if (players.isEmpty()) finished = true;
            else advance(now);
            return finished;
        }

        private MultiplayerGameSnapshot snapshot(Instant now, UUID viewer) {
            requirePlayer(viewer);
            advance(now);
            Round<T> round = current();
            GamePhase phase = phase(now);
            boolean revealed = phase == GamePhase.REVEAL || phase == GamePhase.COMPLETED;
            GameData data = phase == GamePhase.PREVIEW ? definition.preview(round.target)
                    : revealed ? definition.missedResult(round.target) : null;
            Instant ends = switch (phase) {
                case PREVIEW, TRANSITION, INPUT -> round.timeline.endOf(phase);
                case REVEAL -> round.readyDeadline;
                case COMPLETED -> null;
            };
            List<MultiplayerGameSnapshot.Player> playerRows = new ArrayList<>();
            for (var player : players.entrySet()) {
                double total = 0;
                for (int i = 0; i < rounds.size() - (revealed ? 0 : 1); i++) {
                    GameEvaluation evaluation = rounds.get(i).evaluations.get(player.getKey());
                    if (evaluation != null) total += evaluation.score();
                }
                GameEvaluation currentEvaluation = round.evaluations.get(player.getKey());
                playerRows.add(new MultiplayerGameSnapshot.Player(player.getValue(), total,
                        revealed ? currentEvaluation == null ? 0.0 : currentEvaluation.score() : null,
                        revealed ? currentEvaluation != null : player.getKey().equals(viewer) && currentEvaluation != null,
                        revealed && round.ready.contains(player.getKey())));
            }
            List<MultiplayerGameSnapshot.RoundResult> results = new ArrayList<>();
            for (int i = 0; i < rounds.size() - (revealed ? 0 : 1); i++) {
                Round<T> past = rounds.get(i);
                List<MultiplayerGameSnapshot.Score> scores = new ArrayList<>();
                for (var player : players.entrySet()) {
                    GameEvaluation evaluation = past.evaluations.get(player.getKey());
                    scores.add(new MultiplayerGameSnapshot.Score(player.getValue(), evaluation == null ? 0 : evaluation.score()));
                }
                results.add(new MultiplayerGameSnapshot.RoundResult(past.number, definition.missedResult(past.target), scores));
            }
            return new MultiplayerGameSnapshot(id, code, definition.gameType(), finished ? "FINISHED" : "IN_PROGRESS",
                    config.version(), round.number, config.totalRounds(), phase, now, ends, data,
                    round.guesses.containsKey(viewer),
                    round.drafts.containsKey(viewer) ? round.drafts.get(viewer).key() : null,
                    List.copyOf(playerRows), List.copyOf(results));
        }
    }

    private static final class Round<T> {
        private final int number;
        private final T target;
        private final RoundTimeline timeline;
        private final Map<UUID, String> guesses = new HashMap<>();
        private final Map<UUID, GameEvaluation> evaluations = new HashMap<>();
        private final Map<UUID, Draft> drafts = new HashMap<>();
        private final Set<UUID> ready = new HashSet<>();
        private Instant readyDeadline;
        Round(int number, Instant now, T target, GameConfig config) {
            this.number = number;
            this.target = target;
            this.timeline = RoundTimeline.startingAt(now, config);
        }
    }

    private record Draft(long revision, String key, GameEvaluation evaluation) {}
}
