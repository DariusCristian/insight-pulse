package com.insightpulse.ingestion.model;

/**
 * Envelope for every message pushed on {@code /ws/stream}. Matches the frontend's
 * {@code WebSocketMessage} discriminated union (dashboard-frontend/src/types/market.ts).
 *
 * @param type    discriminator, e.g. {@code "SENTIMENT"} or {@code "ANOMALY"}
 * @param payload the typed message body
 */
public record StreamMessage<T>(String type, T payload) {

    public static final String SENTIMENT = "SENTIMENT";
    public static final String ANOMALY = "ANOMALY";
}
