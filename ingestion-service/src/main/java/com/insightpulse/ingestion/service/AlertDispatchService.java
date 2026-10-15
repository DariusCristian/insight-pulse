package com.insightpulse.ingestion.service;

import com.insightpulse.ingestion.model.SentimentAlert;

/**
 * Orchestrates the dispatch of a {@link SentimentAlert} to all downstream consumers
 * (WebSocket broadcast, persistence, metrics, etc.).
 */
public interface AlertDispatchService {

    /**
     * Dispatches the given alert to all registered sinks.
     *
     * @param alert the alert to dispatch
     */
    void dispatch(SentimentAlert alert);
}
