import {
  WS_RECONNECT_DELAY_MS,
  WS_MAX_RECONNECT_ATTEMPTS,
} from '@/lib/constants';
import type { WebSocketMessage } from '@/types/market';

export type MessageHandler = (message: WebSocketMessage) => void;
export type StatusHandler = (status: ConnectionStatus) => void;

export type ConnectionStatus =
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'error';

/**
 * Thin wrapper around the browser WebSocket API that adds:
 *  - typed message parsing
 *  - exponential back-off reconnection
 *  - subscriber callbacks for messages and status changes
 */
export class WebSocketClient {
  private socket: WebSocket | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private shouldReconnect = true;

  private readonly messageHandlers = new Set<MessageHandler>();
  private readonly statusHandlers = new Set<StatusHandler>();

  constructor(private readonly url: string) {}

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  connect(): void {
    if (
      this.socket?.readyState === WebSocket.OPEN ||
      this.socket?.readyState === WebSocket.CONNECTING
    ) {
      return;
    }

    this.shouldReconnect = true;
    this.notifyStatus('connecting');

    this.socket = new WebSocket(this.url);
    this.socket.onopen = this.handleOpen;
    this.socket.onmessage = this.handleMessage;
    this.socket.onclose = this.handleClose;
    this.socket.onerror = this.handleError;
  }

  disconnect(): void {
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    this.socket?.close();
    this.socket = null;
    this.notifyStatus('disconnected');
  }

  /**
   * Register a handler that is called with every parsed WebSocketMessage.
   * Returns an unsubscribe function.
   */
  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  /**
   * Register a handler that is called whenever the connection status changes.
   * Returns an unsubscribe function.
   */
  onStatus(handler: StatusHandler): () => void {
    this.statusHandlers.add(handler);
    return () => this.statusHandlers.delete(handler);
  }

  // ---------------------------------------------------------------------------
  // Private socket event handlers
  // ---------------------------------------------------------------------------

  private readonly handleOpen = (): void => {
    this.reconnectAttempts = 0;
    this.notifyStatus('connected');
  };

  private readonly handleMessage = (event: MessageEvent<string>): void => {
    let parsed: WebSocketMessage;
    try {
      parsed = JSON.parse(event.data) as WebSocketMessage;
    } catch {
      // Malformed frame — skip silently; production code should log to an
      // observability backend here.
      return;
    }
    this.messageHandlers.forEach((h) => h(parsed));
  };

  private readonly handleClose = (): void => {
    this.notifyStatus('disconnected');
    this.scheduleReconnect();
  };

  private readonly handleError = (): void => {
    this.notifyStatus('error');
    this.socket?.close();
  };

  // ---------------------------------------------------------------------------
  // Reconnection logic
  // ---------------------------------------------------------------------------

  private scheduleReconnect(): void {
    if (!this.shouldReconnect) return;
    if (this.reconnectAttempts >= WS_MAX_RECONNECT_ATTEMPTS) return;

    const delay = WS_RECONNECT_DELAY_MS * 2 ** this.reconnectAttempts;
    this.reconnectAttempts += 1;

    this.clearReconnectTimer();
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, delay);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private notifyStatus(status: ConnectionStatus): void {
    this.statusHandlers.forEach((h) => h(status));
  }
}
