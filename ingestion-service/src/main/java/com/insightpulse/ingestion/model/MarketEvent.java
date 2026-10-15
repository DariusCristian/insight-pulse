package com.insightpulse.ingestion.model;

/**
 * Immutable domain record representing a raw market event ingested from the Kafka topic.
 *
 * @param eventId     Unique identifier for this event.
 * @param assetSymbol Ticker symbol (e.g. "AAPL", "BTC-USD").
 * @param type        Event classification (e.g. "NEWS", "TRADE", "QUOTE").
 * @param rawText     Original unprocessed text payload.
 * @param price       Spot price at event time.
 * @param timestampMs Unix epoch milliseconds when the event was produced.
 */
public record MarketEvent(
        String eventId,
        String assetSymbol,
        String type,
        String rawText,
        double price,
        long timestampMs
) {}
