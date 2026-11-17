package com.studexa.quiz.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket/STOMP config for real-time quiz events.
 * - /app/*          -> client-to-server messages (@MessageMapping)
 * - /topic/room/*   -> server-to-client broadcasts (room-specific)
 * - /topic/user/*   -> server-to-client user-specific messages
 *
 * <p>The handshake only accepts the configured frontend origin, and CONNECT
 * frames are authenticated with the same JWT as the REST API so handlers can
 * identify the host and the player instead of trusting the payload.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final String allowedOrigin;

    public WebSocketConfig(JwtDecoder jwtDecoder,
            @Value("${app.frontend-url:http://localhost:5173}") String allowedOrigin) {
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/quiz").setAllowedOriginPatterns(allowedOrigin);
        registry.addEndpoint("/quiz").setAllowedOriginPatterns(allowedOrigin).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        try {
                            Jwt jwt = jwtDecoder.decode(header.substring(7));
                            accessor.setUser(new JwtAuthenticationToken(jwt));
                        } catch (JwtException | IllegalArgumentException ex) {
                            throw new MessagingException("Invalid WebSocket credentials.", ex);
                        }
                    }
                }
                return message;
            }
        });
    }
}
