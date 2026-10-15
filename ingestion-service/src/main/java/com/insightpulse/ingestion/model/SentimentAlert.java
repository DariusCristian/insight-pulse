package com.insightpulse.ingestion.model;

/**
 * Immutable domain record representing a processed sentiment alert produced
 * by the inference-service and dispatched over WebSocket.
 *
 * @param eventId       Correlates back to the originating {@link MarketEvent#eventId()}.
 * @param assetSymbol   Ticker symbol.
 * @param score         Sentiment score in the range [-1.0, 1.0].
 * @param label         Human-readable label (e.g. "POSITIVE", "NEGATIVE", "NEUTRAL").
 * @param processedAtMs Unix epoch milliseconds when inference completed.
 */
public record SentimentAlert(
        String eventId,
        String assetSymbol,
        double score,
        String label,
        long processedAtMs
) {}
