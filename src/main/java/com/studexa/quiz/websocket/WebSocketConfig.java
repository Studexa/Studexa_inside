package com.studexa.quiz.websocket;

import java.util.Set;
import java.util.UUID;

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

import com.studexa.quiz.SessionPlayerRepository;
import com.studexa.quiz.SessionRepository;

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

    private static final String ROOM_TOPIC = "/topic/room/";
    private static final Set<String> COMMANDS = Set.of("/app/quiz.ready", "/app/quiz.start",
            "/app/quiz.next", "/app/quiz.answer", "/app/quiz.end");

    private final JwtDecoder jwtDecoder;
    private final String allowedOrigin;
    private final SessionRepository sessions;
    private final SessionPlayerRepository players;

    public WebSocketConfig(JwtDecoder jwtDecoder,
            @Value("${app.frontend-url:http://localhost:5173}") String allowedOrigin,
            SessionRepository sessions, SessionPlayerRepository players) {
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigin = allowedOrigin;
        this.sessions = sessions;
        this.players = players;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/quiz", "/quiz").setAllowedOriginPatterns(allowedOrigin);
        registry.addEndpoint("/ws/quiz", "/quiz").setAllowedOriginPatterns(allowedOrigin).withSockJS();
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
                if (accessor == null) return message;
                StompCommand command = accessor.getCommand();
                if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new MessagingException("Authentication required.");
                    }
                    try {
                        Jwt jwt = jwtDecoder.decode(header.substring(7));
                        accessor.setUser(new JwtAuthenticationToken(jwt));
                    } catch (JwtException | IllegalArgumentException ex) {
                        throw new MessagingException("Invalid WebSocket credentials.", ex);
                    }
                } else if (command == StompCommand.SUBSCRIBE) {
                    requireRoomMember(accessor);
                } else if (command == StompCommand.SEND && (accessor.getUser() == null
                        || accessor.getDestination() == null || !COMMANDS.contains(accessor.getDestination()))) {
                    throw new MessagingException("Unsupported room command.");
                }
                return message;
            }
        });
    }

    private void requireRoomMember(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (accessor.getUser() == null || destination == null || !destination.startsWith(ROOM_TOPIC)) {
            throw new MessagingException("Room subscription denied.");
        }
        String code = destination.substring(ROOM_TOPIC.length());
        if (!code.matches("[A-Z0-9]{6}")) throw new MessagingException("Room subscription denied.");
        UUID user = UUID.fromString(accessor.getUser().getName());
        if (sessions.findByRoomCode(code).flatMap(room -> players.findBySessionIdAndUserId(room.getId(), user)).isEmpty()) {
            throw new MessagingException("Room subscription denied.");
        }
    }
}
