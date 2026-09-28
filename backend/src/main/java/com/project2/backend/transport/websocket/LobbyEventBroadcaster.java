package com.project2.backend.transport.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.project2.backend.lobby.LobbyEvent;

@Component
public final class LobbyEventBroadcaster {
    private final SimpMessagingTemplate messaging;

    public LobbyEventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @EventListener
    public void onLobbyEvent(LobbyEvent event) {
        messaging.convertAndSend("/topic/lobbies/" + event.code(), event);
    }
}
