package com.project2.backend.transport.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public final class LobbyHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler,
                                      Map<String, Object> attributes) {
        UUID guestId = (UUID) attributes.get(LobbyHandshakeInterceptor.GUEST_ID_ATTRIBUTE);
        return guestId == null ? null : guestId::toString;
    }
}
