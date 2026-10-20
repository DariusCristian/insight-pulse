package com.insightpulse.ingestion.kafka;

/**
 * Thrown when a Kafka record cannot be turned into a valid {@code MarketEvent}
 * (malformed JSON or missing required fields). Retrying cannot fix it, so the
 * error handler sends it straight to the dead-letter topic.
 */
public class InvalidMarketEventException extends RuntimeException {

    public InvalidMarketEventException(String message) {
        super(message);
    }

    public InvalidMarketEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
