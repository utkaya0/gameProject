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
@Table(name = "participant")
public class ParticipantEntity {

    public enum Role {
        HOST, PLAYER
    }

    public enum ConnectionStatus {
        CONNECTED, DISCONNECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lobby_id", nullable = false)
    private LobbyEntity lobby;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", referencedColumnName = "guest_id", nullable = false)
    private GuestSessionEntity guest;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 8)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 16)
    private ConnectionStatus connectionStatus;

    protected ParticipantEntity() {
    }

    public ParticipantEntity(LobbyEntity lobby, GuestSessionEntity guest, String displayName,
                             Role role, Instant joinedAt, ConnectionStatus connectionStatus) {
        this.lobby = lobby;
        this.guest = guest;
        this.displayName = displayName;
        this.role = role;
        this.joinedAt = joinedAt;
        this.connectionStatus = connectionStatus;
    }

    public Long getId() {
        return id;
    }

    public LobbyEntity getLobby() {
        return lobby;
    }

    public GuestSessionEntity getGuest() {
        return guest;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}
