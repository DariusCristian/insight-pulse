package com.insightpulse.ingestion.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    /** Active WebSocket sessions; thread-safe for concurrent virtual-thread access. */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        // TODO: send initial state snapshot to newly connected client if required.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // TODO: handle inbound client messages (e.g. subscription filters).
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    /**
     * Broadcasts a JSON-serialised {@code payload} to all connected sessions.
     *
     * @param payload serialised alert payload
     */
    public void broadcast(String payload) {
        // TODO: iterate sessions, skip closed ones, send TextMessage(payload).
    }
}
