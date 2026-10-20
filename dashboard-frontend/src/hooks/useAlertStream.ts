'use client';

import { useEffect, useState } from 'react';
import type { ConnectionStatus } from '@/lib/websocket';
import { MAX_ALERT_BUFFER } from '@/lib/constants';
import { useStream } from '@/components/providers/StreamProvider';
import type { SentimentAlert } from '@/types/market';

export interface AlertStreamState {
  alerts: SentimentAlert[];
  connectionStatus: ConnectionStatus;
  latestAlert: SentimentAlert | null;
}

/**
 * Accumulates SENTIMENT messages from the shared stream (see StreamProvider),
 * newest first, capped at MAX_ALERT_BUFFER items.
 */
export function useAlertStream(): AlertStreamState {
  const { client, connectionStatus } = useStream();
  const [alerts, setAlerts] = useState<SentimentAlert[]>([]);

  useEffect(() => {
    if (!client) return;
    return client.onMessage((message) => {
      if (message.type !== 'SENTIMENT') return;
      const alert = message.payload;
      setAlerts((prev) => [alert, ...prev].slice(0, MAX_ALERT_BUFFER));
    });
  }, [client]);

  return { alerts, connectionStatus, latestAlert: alerts[0] ?? null };
}
