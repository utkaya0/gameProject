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
@Table(name = "lobby")
public class LobbyEntity {

    public enum Status {
        WAITING, IN_GAME, FINISHED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 6, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_guest_id", referencedColumnName = "guest_id", nullable = false)
    private GuestSessionEntity hostGuest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "max_players", nullable = false)
    private Short maxPlayers;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LobbyEntity() {
    }

    public LobbyEntity(String code, GuestSessionEntity hostGuest, Status status,
                       Short maxPlayers, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.hostGuest = hostGuest;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public GuestSessionEntity getHostGuest() {
        return hostGuest;
    }

    public void setHostGuest(GuestSessionEntity hostGuest) {
        this.hostGuest = hostGuest;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Short getMaxPlayers() {
        return maxPlayers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
