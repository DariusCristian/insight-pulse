'use client';

import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import type { AnomalyAlert } from '@/types/market';

interface AnomalyPanelProps {
  anomalies: AnomalyAlert[];
}

type BadgeVariant = 'success' | 'danger' | 'warning' | 'neutral';

const severityVariant: Record<AnomalyAlert['severity'], BadgeVariant> = {
  LOW: 'neutral',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

/**
 * Panel listing the most recent anomaly alerts, ordered newest-first.
 */
export function AnomalyPanel({ anomalies }: AnomalyPanelProps) {
  return (
    <Card title="Anomaly Alerts">
      <div className="max-h-64 overflow-y-auto divide-y divide-primary-800">
        {anomalies.length === 0 && (
          <p className="py-4 text-center text-sm text-primary-500">
            No anomalies detected.
          </p>
        )}
        {anomalies.map((anomaly) => (
          <div key={anomaly.alertId} className="flex flex-col gap-1 px-1 py-3">
            <div className="flex items-center justify-between gap-2">
              <span className="font-mono text-xs font-semibold text-primary-300 uppercase tracking-wide">
                {anomaly.assetSymbol}
              </span>
              <Badge variant={severityVariant[anomaly.severity]}>
                {anomaly.severity}
              </Badge>
            </div>
            <p className="text-xs text-primary-400 line-clamp-2">
              {anomaly.description}
            </p>
            <span className="text-xs text-primary-600">
              {new Date(anomaly.triggeredAtMs).toLocaleTimeString()}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
