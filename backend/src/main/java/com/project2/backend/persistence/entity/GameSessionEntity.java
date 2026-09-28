package com.project2.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_session")
public class GameSessionEntity {

    public enum Mode {
        SINGLE_PLAYER, MULTIPLAYER
    }

    public enum Status {
        IN_PROGRESS, FINISHED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_type", nullable = false, length = 32)
    private String gameType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private Mode mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lobby_id")
    private LobbyEntity lobby;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "current_round", nullable = false)
    private Short currentRound;

    @Column(name = "total_rounds", nullable = false)
    private Short totalRounds;

    @Column(name = "config_version", nullable = false, length = 32)
    private String configVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected GameSessionEntity() {
    }

    public GameSessionEntity(String gameType, Mode mode, LobbyEntity lobby, Status status,
                             Short currentRound, Short totalRounds, String configVersion, Instant startedAt) {
        this.gameType = gameType;
        this.mode = mode;
        this.lobby = lobby;
        this.status = status;
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.configVersion = configVersion;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public String getGameType() {
        return gameType;
    }

    public Mode getMode() {
        return mode;
    }

    public LobbyEntity getLobby() {
        return lobby;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Short getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Short currentRound) {
        this.currentRound = currentRound;
    }

    public Short getTotalRounds() {
        return totalRounds;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
