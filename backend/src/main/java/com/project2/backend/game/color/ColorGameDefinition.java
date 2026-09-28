package com.project2.backend.game.color;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.project2.backend.common.GameConfig;
import com.project2.backend.game.session.GameData;
import com.project2.backend.game.session.GameDefinition;
import com.project2.backend.game.session.GameEvaluation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class ColorGameDefinition implements GameDefinition<SrgbColor, SrgbColor> {
    private final ColorGameConfig config;
    private final ColorGenerator generator;
    private final ColorScorer scorer;

    public ColorGameDefinition() {
        this(ColorGameConfig.initial(), new ColorGenerator());
    }

    public ColorGameDefinition(ColorGameConfig config, ColorGenerator generator) {
        this.config = config;
        this.generator = generator;
        this.scorer = new ColorScorer(config);
    }

    @Override
    public String gameType() {
        return "COLOR_GUESS";
    }

    @Override
    public GameConfig config() {
        return config.session();
    }

    @Override
    public SrgbColor createTarget() {
        return generator.next();
    }

    @Override
    public SrgbColor parseGuess(Map<String, Object> input) {
        Object value = input == null ? null : input.get("color");
        if (!(value instanceof String color)) {
            throw new IllegalArgumentException("guess.color must be #RRGGBB");
        }
        return SrgbColor.parse(color);
    }

    @Override
    public String guessKey(SrgbColor guess) {
        return guess.toHex();
    }

    @Override
    public GameData preview(SrgbColor target) {
        return new ColorPreview(target.toHex());
    }

    @Override
    public GameEvaluation evaluate(SrgbColor target, SrgbColor guess) {
        ColorScore result = scorer.score(target.toHex(), guess.toHex());
        return new GameEvaluation(result.score(),
                new ColorResult(target.toHex(), guess.toHex(), result.colorDistance()));
    }

    @Override
    public GameData missedResult(SrgbColor target) {
        return new ColorResult(target.toHex(), null, null);
    }

    public record ColorPreview(String targetColor) implements GameData {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColorResult(String targetColor, String guessedColor, Double colorDistance) implements GameData {
    }
}
