'use client';

import { useEffect, useState } from 'react';
import type { ConnectionStatus } from '@/lib/websocket';
import { MAX_ALERT_BUFFER } from '@/lib/constants';
import { useStream } from '@/components/providers/StreamProvider';
import type { AnomalyAlert } from '@/types/market';

export interface AnomalyAlertsState {
  anomalies: AnomalyAlert[];
  connectionStatus: ConnectionStatus;
  latestAnomaly: AnomalyAlert | null;
}

/**
 * Accumulates ANOMALY messages from the shared stream (see StreamProvider),
 * newest first, capped at MAX_ALERT_BUFFER items.
 */
export function useAnomalyAlerts(): AnomalyAlertsState {
  const { client, connectionStatus } = useStream();
  const [anomalies, setAnomalies] = useState<AnomalyAlert[]>([]);

  useEffect(() => {
    if (!client) return;
    return client.onMessage((message) => {
      if (message.type !== 'ANOMALY') return;
      const alert = message.payload;
      setAnomalies((prev) => [alert, ...prev].slice(0, MAX_ALERT_BUFFER));
    });
  }, [client]);

  return { anomalies, connectionStatus, latestAnomaly: anomalies[0] ?? null };
}
