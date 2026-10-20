// ---------------------------------------------------------------------------
// Application-wide constants sourced from environment variables.
// All public runtime values are prefixed with NEXT_PUBLIC_ so Next.js
// inlines them at build time.
// ---------------------------------------------------------------------------

/** WebSocket endpoint for the real-time event stream. */
export const WS_ENDPOINT: string =
  process.env.NEXT_PUBLIC_WS_ENDPOINT ?? 'ws://localhost:8080/ws/stream';

/** Base URL for the ingestion-service REST API. */
export const API_BASE_URL: string =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

/** Maximum number of alerts kept in each in-memory feed. */
export const MAX_ALERT_BUFFER = 50 as const;

/** Reconnect back-off base delay in milliseconds. */
export const WS_RECONNECT_DELAY_MS = 2_000 as const;

/** Maximum reconnect attempts before giving up. */
export const WS_MAX_RECONNECT_ATTEMPTS = 10 as const;
