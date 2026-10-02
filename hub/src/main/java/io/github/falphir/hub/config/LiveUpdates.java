package io.github.falphir.hub.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import io.github.falphir.hub.service.HubEvent;

/**
 * Pushes {@link HubEvent}s to open dashboards at /api/admin/live, so they reload what changed
 * instead of waiting for their next poll. Messages only say what changed; the dashboard fetches
 * the data itself through the normal API.
 *
 * The handshake is an ordinary /api/admin/** request, so SecurityConfig requires an admin JWT; browsers
 * can't set headers on a WebSocket, so it comes as ?access_token=. Because auth is that token and not
 * a cookie, any origin may connect (the token is what a hostile page wouldn't have).
 */
@Configuration
@EnableWebSocket
public class LiveUpdates extends TextWebSocketHandler implements WebSocketConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveUpdates.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "/api/admin/live").setAllowedOriginPatterns("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // the decorator serialises sends, since broadcasts can come from several threads at once
        sessions.add(new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.removeIf(s -> s.getId().equals(session.getId()));
    }

    /** Runs after the change is committed, so a dashboard reloading straight away sees it. */
    @TransactionalEventListener(fallbackExecution = true)
    public void broadcast(HubEvent event) {
        String json = event.serverId() == null
                ? "{\"topic\":\"%s\"}".formatted(event.topic())
                : "{\"topic\":\"%s\",\"serverId\":\"%s\"}".formatted(event.topic(), event.serverId());
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (tokenExpired(session)) {
                close(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException | IllegalStateException e) {
                LOGGER.debug("Dropping live session {}: {}", session.getId(), e.getMessage());
                close(session);
            }
        }
    }

    /** An open socket outlives its token otherwise; cut it off once the admin's sign-in expires. */
    private static boolean tokenExpired(WebSocketSession session) {
        return session.getPrincipal() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getExpiresAt() != null
                && jwt.getToken().getExpiresAt().isBefore(Instant.now());
    }

    private void close(WebSocketSession session) {
        sessions.remove(session);
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // already gone
        }
    }
}
