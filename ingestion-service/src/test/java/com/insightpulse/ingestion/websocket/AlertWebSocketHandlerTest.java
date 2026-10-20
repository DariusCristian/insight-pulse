package com.insightpulse.ingestion.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertWebSocketHandlerTest {

    private AlertWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AlertWebSocketHandler();
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static String sentPayload(WebSocketSession session) throws IOException {
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        return ((TextMessage) captor.getValue()).getPayload();
    }

    @Test
    void broadcastsToAllConnectedSessions() throws Exception {
        WebSocketSession a = openSession("a");
        WebSocketSession b = openSession("b");
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);

        handler.broadcast("{\"hello\":\"world\"}");

        assertThat(sentPayload(a)).isEqualTo("{\"hello\":\"world\"}");
        assertThat(sentPayload(b)).isEqualTo("{\"hello\":\"world\"}");
        assertThat(handler.activeSessionCount()).isEqualTo(2);
    }

    @Test
    void removesSessionOnDisconnect() throws Exception {
        WebSocketSession a = openSession("a");
        handler.afterConnectionEstablished(a);
        handler.afterConnectionClosed(a, CloseStatus.NORMAL);

        handler.broadcast("x");

        verify(a, never()).sendMessage(any());
        assertThat(handler.activeSessionCount()).isZero();
    }

    @Test
    void skipsAndRemovesSessionsThatAreAlreadyClosed() throws Exception {
        WebSocketSession dead = openSession("dead");
        WebSocketSession alive = openSession("alive");
        handler.afterConnectionEstablished(dead);
        handler.afterConnectionEstablished(alive);
        when(dead.isOpen()).thenReturn(false);

        handler.broadcast("x");

        verify(dead, never()).sendMessage(any());
        assertThat(sentPayload(alive)).isEqualTo("x");
        assertThat(handler.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void failingSessionIsDroppedAndDoesNotBlockOthers() throws Exception {
        WebSocketSession broken = openSession("broken");
        WebSocketSession healthy = openSession("healthy");
        doThrow(new IOException("connection reset")).when(broken).sendMessage(any());
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);

        handler.broadcast("x");

        assertThat(sentPayload(healthy)).isEqualTo("x");
        verify(broken).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertThat(handler.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void transportErrorDropsSession() throws Exception {
        WebSocketSession a = openSession("a");
        handler.afterConnectionEstablished(a);

        handler.handleTransportError(a, new IOException("boom"));

        assertThat(handler.activeSessionCount()).isZero();
        verify(a).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    void broadcastWithNoSessionsIsANoOp() {
        handler.broadcast("x"); // must not throw
        assertThat(handler.activeSessionCount()).isZero();
    }

    @Test
    void concurrentConnectDisconnectAndBroadcastIsSafe() throws Exception {
        // Kept small on purpose: every broadcast reaches every open mock session and Mockito
        // records each call, so memory grows ~quadratically with the session count
        // (16 x 200 sessions exhausted the test heap).
        int threads = 8;
        int perThread = 50;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int id = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        WebSocketSession s = openSession("s-" + id + "-" + i);
                        handler.afterConnectionEstablished(s);
                        handler.broadcast("msg-" + i);
                        if (i % 2 == 0) {
                            handler.afterConnectionClosed(s, CloseStatus.NORMAL);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(); // propagates any exception from inside a task
            }
        }
        // Every odd-numbered session (half per thread) was left connected.
        assertThat(handler.activeSessionCount()).isEqualTo(threads * perThread / 2);
    }
}
