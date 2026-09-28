package com.project2.backend.game.session;

import com.project2.backend.common.GameConfig;
import java.util.Map;

/** A game supplies its content and scoring; the session owns time and submissions. */
public interface GameDefinition<Target, Guess> {
    String gameType();

    GameConfig config();

    Target createTarget();

    Guess parseGuess(Map<String, Object> input);

    String guessKey(Guess guess);

    GameData preview(Target target);

    GameEvaluation evaluate(Target target, Guess guess);

    GameData missedResult(Target target);
}
