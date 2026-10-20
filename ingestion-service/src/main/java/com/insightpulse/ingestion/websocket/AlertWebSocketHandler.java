package com.insightpulse.ingestion.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    /** Max time a single send may block before the client is considered stuck and dropped. */
    static final int SEND_TIME_LIMIT_MS = 5_000;

    /** Max bytes buffered for a slow client before it is dropped. */
    static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    /**
     * Active sessions keyed by session id. Each session is wrapped in a
     * {@link ConcurrentWebSocketSessionDecorator} because {@code WebSocketSession.sendMessage}
     * is not safe for concurrent calls on the same session, and several virtual threads may
     * broadcast at once.
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
        log.info("WebSocket client connected: {} (active: {})", session.getId(), sessions.size());
        // TODO: send initial state snapshot to newly connected client if required.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // TODO: handle inbound client messages (e.g. subscription filters).
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error for {}: {}", session.getId(), exception.toString());
        drop(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("WebSocket client disconnected: {} ({}) (active: {})",
                session.getId(), status, sessions.size());
    }

    /**
     * Broadcasts a JSON-serialised {@code payload} to all connected sessions.
     *
     * <p>Closed sessions are removed. A session that fails (I/O error, send timeout, buffer limit
     * exceeded) is dropped and closed; it never prevents delivery to the remaining sessions.
     *
     * @param payload serialised alert payload
     */
    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                sessions.remove(entry.getKey());
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                log.warn("Dropping WebSocket client {}: {}", entry.getKey(), e.toString());
                drop(entry.getKey());
            }
        }
    }

    /** Number of currently registered sessions. */
    public int activeSessionCount() {
        return sessions.size();
    }

    private void drop(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session != null) {
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException | RuntimeException ignored) {
                // already broken; nothing more to do
            }
        }
    }
}
