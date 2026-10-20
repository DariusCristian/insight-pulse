'use client';

import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { scoreColor } from './SentimentChart';
import type { SentimentAlert } from '@/types/market';

interface AlertFeedProps {
  alerts: SentimentAlert[];
}

const labelVariant: Record<SentimentAlert['label'], 'success' | 'danger' | 'neutral'> =
  {
    POSITIVE: 'success',
    NEGATIVE: 'danger',
    NEUTRAL: 'neutral',
  };

/**
 * Scrollable feed of the most recent SentimentAlert entries.
 */
export function AlertFeed({ alerts }: AlertFeedProps) {
  return (
    <Card title="Sentiment Alert Feed">
      <div className="max-h-64 overflow-y-auto divide-y divide-primary-800">
        {alerts.length === 0 && (
          <p className="py-4 text-center text-sm text-primary-500">
            Waiting for alerts…
          </p>
        )}
        {alerts.map((alert) => (
          <div
            key={alert.eventId}
            className="flex items-center justify-between gap-4 px-1 py-3"
          >
            <div className="flex items-center gap-3 min-w-0">
              <span className="shrink-0 font-mono text-xs font-semibold text-primary-300 uppercase tracking-wide">
                {alert.assetSymbol}
              </span>
              <span className="truncate text-xs text-primary-400">
                {new Date(alert.processedAtMs).toLocaleTimeString()}
              </span>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <span
                className="font-mono text-xs"
                style={{ color: scoreColor(alert.score) }}
              >
                {alert.score >= 0 ? '+' : ''}
                {alert.score.toFixed(2)}
              </span>
              <Badge variant={labelVariant[alert.label]}>
                {alert.label}
              </Badge>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}
