package com.project2.backend.transport.websocket;

import com.project2.backend.guest.GuestSessionService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
public final class LobbyHandshakeInterceptor implements HandshakeInterceptor {
    public static final String GUEST_ID_ATTRIBUTE = "guestId";
    private final GuestSessionService guests;

    public LobbyHandshakeInterceptor(GuestSessionService guests) {
        this.guests = guests;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        UUID guestId = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("project_guest".equals(cookie.getName())) {
                        guestId = guests.findExisting(cookie.getValue());
                        break;
                    }
                }
            }
        }
        if (guestId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(GUEST_ID_ATTRIBUTE, guestId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
