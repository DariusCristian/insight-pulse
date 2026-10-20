'use client';

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { WebSocketClient } from '@/lib/websocket';
import type { ConnectionStatus } from '@/lib/websocket';
import { WS_ENDPOINT } from '@/lib/constants';

interface StreamContextValue {
  client: WebSocketClient | null;
  connectionStatus: ConnectionStatus;
}

const StreamContext = createContext<StreamContextValue | null>(null);

/**
 * Owns the single WebSocket connection to ingestion-service `/ws/stream`.
 * SENTIMENT and ANOMALY messages arrive on the same socket, so every hook
 * below this provider shares one connection instead of opening its own.
 */
export function StreamProvider({ children }: { children: ReactNode }) {
  const [client, setClient] = useState<WebSocketClient | null>(null);
  const [connectionStatus, setConnectionStatus] =
    useState<ConnectionStatus>('disconnected');

  useEffect(() => {
    const ws = new WebSocketClient(WS_ENDPOINT);
    const unsubStatus = ws.onStatus(setConnectionStatus);
    setClient(ws);
    ws.connect();

    return () => {
      unsubStatus();
      ws.disconnect();
      setClient(null);
    };
  }, []);

  return (
    <StreamContext.Provider value={{ client, connectionStatus }}>
      {children}
    </StreamContext.Provider>
  );
}

/** Access the shared stream. Must be used inside a {@link StreamProvider}. */
export function useStream(): StreamContextValue {
  const ctx = useContext(StreamContext);
  if (ctx === null) {
    throw new Error('useStream must be used inside a <StreamProvider>');
  }
  return ctx;
}
