'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { WebSocketClient } from '@/lib/websocket';
import type { ConnectionStatus } from '@/lib/websocket';
import { WS_ENDPOINT, MAX_ALERT_BUFFER } from '@/lib/constants';
import type { SentimentAlert, WebSocketMessage } from '@/types/market';

export interface AlertStreamState {
  alerts: SentimentAlert[];
  connectionStatus: ConnectionStatus;
  latestAlert: SentimentAlert | null;
}

/**
 * Connects to the ingestion-service WebSocket stream and accumulates
 * incoming SENTIMENT messages.  The connection is established on mount
 * and torn down on unmount.  Reconnection is handled by WebSocketClient.
 */
export function useAlertStream(): AlertStreamState {
  const clientRef = useRef<WebSocketClient | null>(null);

  const [alerts, setAlerts] = useState<SentimentAlert[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<ConnectionStatus>('disconnected');
  const [latestAlert, setLatestAlert] = useState<SentimentAlert | null>(null);

  const handleMessage = useCallback((message: WebSocketMessage): void => {
    if (message.type !== 'SENTIMENT') return;

    const alert = message.payload;

    setLatestAlert(alert);
    setAlerts((prev) => {
      const next = [alert, ...prev];
      // Trim the buffer so we don't accumulate unbounded memory.
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

  return { alerts, connectionStatus, latestAlert };
}
