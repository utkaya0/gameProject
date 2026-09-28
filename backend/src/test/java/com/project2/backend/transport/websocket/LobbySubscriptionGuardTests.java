package com.project2.backend.transport.websocket;

import com.project2.backend.lobby.LobbyService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LobbySubscriptionGuardTests {
    private final LobbyService lobbies = new LobbyService(event -> {});
    private final LobbySubscriptionGuard guard = new LobbySubscriptionGuard(lobbies);

    @Test
    void allowsOnlyMembersOfExactLobbyTopicAndRejectsClientMessages() {
        UUID host = UUID.randomUUID();
        String code = lobbies.create(host, "Ada", 2).code();
        Message<?> allowed = frame(StompCommand.SUBSCRIBE, "/topic/lobbies/" + code, host);
        assertSame(allowed, guard.preSend(allowed, null));
        assertThrows(IllegalArgumentException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/lobbies/" + code,
                        UUID.randomUUID()), null));
        assertThrows(IllegalArgumentException.class,
                () -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/lobbies/" + code + "/other", host), null));
        assertThrows(IllegalArgumentException.class,
                () -> guard.preSend(frame(StompCommand.SEND, "/topic/lobbies/" + code, host), null));
        lobbies.leave(host, code);
        assertThrows(IllegalArgumentException.class,
                () -> guard.preSend(allowed, null));
    }

    private Message<?> frame(StompCommand command, String destination, UUID guestId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(guestId::toString);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
