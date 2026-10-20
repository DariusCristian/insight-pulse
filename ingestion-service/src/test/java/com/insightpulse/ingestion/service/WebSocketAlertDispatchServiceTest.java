package com.insightpulse.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
