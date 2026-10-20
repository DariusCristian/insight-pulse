package com.insightpulse.ingestion.service;

import com.insightpulse.ingestion.model.AnomalyAlert;
import com.insightpulse.ingestion.model.SentimentAlert;

/**
 * Pushes processed alerts to downstream consumers (currently the dashboard WebSocket).
 */
public interface AlertDispatchService {

    /**
     * Dispatches a sentiment alert.
     *
     * @param alert the alert to dispatch
     */
    void dispatch(SentimentAlert alert);

    /**
     * Dispatches an anomaly alert.
     *
     * @param alert the alert to dispatch
     */
    void dispatchAnomaly(AnomalyAlert alert);
}
