package com.insightpulse.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightpulse.ingestion.model.AnomalyAlert;
import com.insightpulse.ingestion.model.SentimentAlert;
import com.insightpulse.ingestion.websocket.AlertWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketAlertDispatchServiceTest {

    @Test
    void broadcastsSentimentAlertInFrontendEnvelopeFormat() throws Exception {
        AlertWebSocketHandler handler = mock(AlertWebSocketHandler.class);
        ObjectMapper mapper = new ObjectMapper();
        WebSocketAlertDispatchService service = new WebSocketAlertDispatchService(handler, mapper);

        service.dispatch(new SentimentAlert("evt-1", "AAPL", 0.8, "POSITIVE", 1_700_000_000_123L));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(handler).broadcast(json.capture());
        JsonNode root = mapper.readTree(json.getValue());

        assertThat(root.get("type").asText()).isEqualTo("SENTIMENT");
        JsonNode payload = root.get("payload");
        assertThat(payload.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(payload.get("assetSymbol").asText()).isEqualTo("AAPL");
        assertThat(payload.get("score").asDouble()).isEqualTo(0.8);
        assertThat(payload.get("label").asText()).isEqualTo("POSITIVE");
        assertThat(payload.get("processedAtMs").asLong()).isEqualTo(1_700_000_000_123L);
    }

    @Test
    void broadcastsAnomalyAlertInFrontendEnvelopeFormat() throws Exception {
        AlertWebSocketHandler handler = mock(AlertWebSocketHandler.class);
        ObjectMapper mapper = new ObjectMapper();
        WebSocketAlertDispatchService service = new WebSocketAlertDispatchService(handler, mapper);

        service.dispatchAnomaly(new AnomalyAlert("a-1", "AAPL", "HIGH", "BEARISH_SPIKE", -4.2, -0.9,
                0.1, 0.05, "evt-9", 1_700_000_000_999L, "AAPL sentiment dropped sharply"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(handler).broadcast(json.capture());
        JsonNode root = mapper.readTree(json.getValue());

        assertThat(root.get("type").asText()).isEqualTo("ANOMALY");
        JsonNode payload = root.get("payload");
        // Fields the frontend AnomalyAlert type reads.
        assertThat(payload.get("alertId").asText()).isEqualTo("a-1");
        assertThat(payload.get("assetSymbol").asText()).isEqualTo("AAPL");
        assertThat(payload.get("severity").asText()).isEqualTo("HIGH");
        assertThat(payload.get("triggeredAtMs").asLong()).isEqualTo(1_700_000_000_999L);
        assertThat(payload.get("description").asText()).isEqualTo("AAPL sentiment dropped sharply");
        assertThat(payload.get("zScore").asDouble()).isEqualTo(-4.2);
    }
}
