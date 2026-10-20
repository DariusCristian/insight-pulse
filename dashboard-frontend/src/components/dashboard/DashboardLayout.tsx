'use client';

import { StreamProvider } from '@/components/providers/StreamProvider';
import { useAlertStream } from '@/hooks/useAlertStream';
import { useAnomalyAlerts } from '@/hooks/useAnomalyAlerts';
import { SentimentChart } from './SentimentChart';
import { AlertFeed } from './AlertFeed';
import { AnomalyPanel } from './AnomalyPanel';
import { ConnectionStatusBadge } from './ConnectionStatusBadge';

/**
 * Top-level dashboard shell. Opens one shared WebSocket connection
 * (StreamProvider) for all live data on the page.
 */
export function DashboardLayout() {
  return (
    <StreamProvider>
      <DashboardContent />
    </StreamProvider>
  );
}

/**
 * Owns the stream hooks so child components receive data as props —
 * keeping data-fetching concerns out of the leaf components.
 */
function DashboardContent() {
  const { alerts, connectionStatus } = useAlertStream();
  const { anomalies } = useAnomalyAlerts();

  return (
    <div className="flex h-screen overflow-hidden bg-primary-950">
      {/* Sidebar */}
      <aside className="flex w-64 shrink-0 flex-col border-r border-primary-800 bg-primary-900 px-4 py-6">
        <div className="mb-8 flex items-center gap-2">
          <span className="text-lg font-bold tracking-tight text-accent-400">
            InsightPulse
          </span>
        </div>

        <nav className="flex flex-col gap-1 text-sm text-primary-300">
          <a
            href="/"
            className="rounded-md px-3 py-2 transition-colors hover:bg-primary-800 hover:text-primary-100"
          >
            Overview
          </a>
          <a
            href="/dashboard"
            className="rounded-md px-3 py-2 transition-colors hover:bg-primary-800 hover:text-primary-100"
          >
            Dashboard
          </a>
        </nav>

        <div className="mt-auto flex flex-col gap-2">
          <ConnectionStatusBadge status={connectionStatus} label="Live stream" />
        </div>
      </aside>

      {/* Main content */}
      <main className="flex flex-1 flex-col overflow-y-auto p-6 gap-6">
        <header>
          <h1 className="text-2xl font-semibold text-primary-50">
            Market Intelligence
          </h1>
          <p className="text-sm text-primary-400">Real-time sentiment &amp; anomaly feed</p>
        </header>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Sentiment chart takes up 2/3 */}
          <section className="lg:col-span-2">
            <SentimentChart alerts={alerts} />
          </section>

          {/* Anomaly panel takes 1/3 */}
          <section>
            <AnomalyPanel anomalies={anomalies} />
          </section>
        </div>

        {/* Alert feed spans full width */}
        <AlertFeed alerts={alerts} />
      </main>
    </div>
  );
}
