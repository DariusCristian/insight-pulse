package com.insightpulse.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightpulse.ingestion.model.AnomalyAlert;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.ingestion.model.StreamMessage;
import com.insightpulse.ingestion.websocket.AlertWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dispatches alerts to dashboard clients by wrapping them in a {@link StreamMessage} envelope,
 * serialising to JSON and broadcasting over WebSocket.
 */
@Service
@RequiredArgsConstructor
public class WebSocketAlertDispatchService implements AlertDispatchService {

    private final AlertWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void dispatch(SentimentAlert alert) {
        broadcast(new StreamMessage<>(StreamMessage.SENTIMENT, alert), alert.eventId());
    }

    @Override
    public void dispatchAnomaly(AnomalyAlert alert) {
        broadcast(new StreamMessage<>(StreamMessage.ANOMALY, alert), alert.alertId());
    }

    private void broadcast(StreamMessage<?> message, String id) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // A record of primitives/strings should always serialise; failure means a programming error.
            throw new IllegalStateException("Failed to serialise " + message.type() + " message " + id, e);
        }
        webSocketHandler.broadcast(json);
    }
}
