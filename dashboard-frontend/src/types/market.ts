// ---------------------------------------------------------------------------
// Market domain types shared across the dashboard-frontend.
// These mirror the data contracts produced by ingestion-service and
// inference-service — keep in sync with the backend DTOs.
// ---------------------------------------------------------------------------

/** A raw financial event arriving from the ingestion pipeline. */
export interface MarketEvent {
  eventId: string;
  assetSymbol: string;
  type: 'TRADE' | 'NEWS';
  rawText: string;
  price: number;
  timestampMs: number;
}

/** Sentiment classification result emitted by the inference service. */
export interface SentimentAlert {
  eventId: string;
  assetSymbol: string;
  /** Sentiment confidence score in the range [0, 1]. */
  score: number;
  label: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  processedAtMs: number;
}

/** Anomaly detection alert emitted by the inference service. */
export interface AnomalyAlert {
  alertId: string;
  assetSymbol: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  triggeredAtMs: number;
  description: string;
}

// ---------------------------------------------------------------------------
// WebSocket message discriminated union
// ---------------------------------------------------------------------------

export interface SentimentWebSocketMessage {
  type: 'SENTIMENT';
  payload: SentimentAlert;
}

export interface AnomalyWebSocketMessage {
  type: 'ANOMALY';
  payload: AnomalyAlert;
}

/**
 * Every message arriving on the /ws/stream endpoint is one of these shapes.
 * Use the `type` discriminant to narrow to the correct payload type.
 */
export type WebSocketMessage = SentimentWebSocketMessage | AnomalyWebSocketMessage;
