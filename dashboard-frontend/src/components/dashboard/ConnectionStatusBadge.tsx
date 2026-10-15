'use client';

import type { ConnectionStatus } from '@/lib/websocket';

interface ConnectionStatusBadgeProps {
  status: ConnectionStatus;
  label?: string;
}

const statusConfig: Record<
  ConnectionStatus,
  { dot: string; text: string; label: string }
> = {
  connecting: {
    dot: 'bg-yellow-400 animate-pulse',
    text: 'text-yellow-300',
    label: 'Connecting',
  },
  connected: {
    dot: 'bg-accent-400',
    text: 'text-accent-400',
    label: 'Connected',
  },
  disconnected: {
    dot: 'bg-primary-500',
    text: 'text-primary-400',
    label: 'Disconnected',
  },
  error: {
    dot: 'bg-danger-500 animate-pulse',
    text: 'text-danger-400',
    label: 'Error',
  },
};

/**
 * Small inline badge indicating the current WebSocket connection state.
 */
export function ConnectionStatusBadge({
  status,
  label,
}: ConnectionStatusBadgeProps) {
  const config = statusConfig[status];

  return (
    <div className="flex items-center gap-2 text-xs">
      <span
        className={`h-2 w-2 rounded-full shrink-0 ${config.dot}`}
        aria-hidden="true"
      />
      <span className={config.text}>
        {label ? `${label}: ` : ''}
        {config.label}
      </span>
    </div>
  );
}
