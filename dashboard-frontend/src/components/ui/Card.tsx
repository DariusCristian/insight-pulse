import type { ReactNode } from 'react';

interface CardProps {
  title?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Surface container used throughout the dashboard.
 * Provides consistent padding, border, and background.
 */
export function Card({ title, children, className = '' }: CardProps) {
  return (
    <div
      className={[
        'rounded-xl border border-primary-800 bg-primary-900',
        'p-4 shadow-sm',
        className,
      ].join(' ')}
    >
      {title && (
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-primary-400">
          {title}
        </h2>
      )}
      {children}
    </div>
  );
}
