package com.project2.backend.transport.websocket;

import com.project2.backend.lobby.LobbyException;
import com.project2.backend.lobby.LobbyService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class LobbySubscriptionGuard implements ChannelInterceptor {
    private static final Pattern LOBBY_TOPIC = Pattern.compile("^/topic/lobbies/([A-Z0-9]{6})$");
    private final LobbyService lobbies;

    public LobbySubscriptionGuard(LobbyService lobbies) {
        this.lobbies = lobbies;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        StompCommand command = headers.getCommand();
        if (command == StompCommand.SEND) {
            throw new IllegalArgumentException("Client messages are not accepted");
        }
        if (command == StompCommand.SUBSCRIBE) {
            Principal user = headers.getUser();
            String destination = headers.getDestination();
            Matcher match = LOBBY_TOPIC.matcher(destination == null ? "" : destination);
            if (user == null || !match.matches()) {
                throw new IllegalArgumentException("Lobby subscription is not allowed");
            }
            try {
                lobbies.get(UUID.fromString(user.getName()), match.group(1));
            } catch (LobbyException | IllegalArgumentException exception) {
                throw new IllegalArgumentException("Lobby subscription is not allowed", exception);
            }
        }
        return message;
    }
}
