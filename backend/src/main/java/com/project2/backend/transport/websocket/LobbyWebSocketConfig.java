package com.project2.backend.transport.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class LobbyWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final LobbyHandshakeInterceptor handshakeInterceptor;
    private final LobbyHandshakeHandler handshakeHandler;
    private final LobbySubscriptionGuard subscriptionGuard;
    private final String[] allowedOrigins;

    public LobbyWebSocketConfig(LobbyHandshakeInterceptor handshakeInterceptor,
                                LobbyHandshakeHandler handshakeHandler,
                                LobbySubscriptionGuard subscriptionGuard,
                                @Value("${app.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                                String allowedOrigins) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.subscriptionGuard = subscriptionGuard;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/ws")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionGuard);
    }
}
