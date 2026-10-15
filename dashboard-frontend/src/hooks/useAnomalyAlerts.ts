'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { WebSocketClient } from '@/lib/websocket';
import type { ConnectionStatus } from '@/lib/websocket';
import { WS_ENDPOINT, MAX_ALERT_BUFFER } from '@/lib/constants';
import type { AnomalyAlert, WebSocketMessage } from '@/types/market';

export interface AnomalyAlertsState {
  anomalies: AnomalyAlert[];
  connectionStatus: ConnectionStatus;
  latestAnomaly: AnomalyAlert | null;
}

/**
 * Connects to the ingestion-service WebSocket stream and accumulates
 * incoming ANOMALY messages.
 *
 * Note: if both useAlertStream and useAnomalyAlerts are mounted simultaneously
 * they will each open their own WebSocket connection.  For production use,
 * consider lifting the WebSocketClient into a React context so a single
 * connection is shared across hooks.
 */
export function useAnomalyAlerts(): AnomalyAlertsState {
  const clientRef = useRef<WebSocketClient | null>(null);

  const [anomalies, setAnomalies] = useState<AnomalyAlert[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<ConnectionStatus>('disconnected');
  const [latestAnomaly, setLatestAnomaly] = useState<AnomalyAlert | null>(null);

  const handleMessage = useCallback((message: WebSocketMessage): void => {
    if (message.type !== 'ANOMALY') return;

    const alert = message.payload;

    setLatestAnomaly(alert);
    setAnomalies((prev) => {
      const next = [alert, ...prev];
      return next.length > MAX_ALERT_BUFFER ? next.slice(0, MAX_ALERT_BUFFER) : next;
    });
  }, []);

  useEffect(() => {
    const client = new WebSocketClient(WS_ENDPOINT);
    clientRef.current = client;

    const unsubMessage = client.onMessage(handleMessage);
    const unsubStatus = client.onStatus(setConnectionStatus);

    client.connect();

    return () => {
      unsubMessage();
      unsubStatus();
      client.disconnect();
      clientRef.current = null;
    };
  }, [handleMessage]);

  return { anomalies, connectionStatus, latestAnomaly };
}
