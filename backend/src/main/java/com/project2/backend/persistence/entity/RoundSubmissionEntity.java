package com.project2.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "round_submission")
public class RoundSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSessionEntity gameSession;

    @Column(name = "round_number", nullable = false)
    private Short roundNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private ParticipantEntity participant;

    @Column(name = "target_color", nullable = false, length = 7)
    private String targetColor;

    @Column(name = "guessed_color", nullable = false, length = 7)
    private String guessedColor;

    @Column(name = "score", nullable = false, precision = 12, scale = 6)
    private BigDecimal score;

    @Column(name = "color_distance", nullable = false, precision = 12, scale = 6)
    private BigDecimal colorDistance;

    @Column(name = "response_time_ms", nullable = false)
    private Integer responseTimeMs;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected RoundSubmissionEntity() {
    }

    public RoundSubmissionEntity(GameSessionEntity gameSession, Short roundNumber, ParticipantEntity participant,
                                 String targetColor, String guessedColor, BigDecimal score,
                                 BigDecimal colorDistance, Integer responseTimeMs, Instant submittedAt) {
        this.gameSession = gameSession;
        this.roundNumber = roundNumber;
        this.participant = participant;
        this.targetColor = targetColor;
        this.guessedColor = guessedColor;
        this.score = score;
        this.colorDistance = colorDistance;
        this.responseTimeMs = responseTimeMs;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public GameSessionEntity getGameSession() {
        return gameSession;
    }

    public Short getRoundNumber() {
        return roundNumber;
    }

    public ParticipantEntity getParticipant() {
        return participant;
    }

    public String getTargetColor() {
        return targetColor;
    }

    public String getGuessedColor() {
        return guessedColor;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getColorDistance() {
        return colorDistance;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
