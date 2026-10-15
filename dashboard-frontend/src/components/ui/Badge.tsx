import type { ReactNode } from 'react';

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'neutral' | 'info';

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
}

const variantClasses: Record<BadgeVariant, string> = {
  success: 'bg-accent-900 text-accent-300 ring-accent-700',
  danger:  'bg-danger-950 text-danger-300 ring-danger-700',
  warning: 'bg-yellow-950 text-yellow-300 ring-yellow-700',
  neutral: 'bg-primary-800 text-primary-300 ring-primary-700',
  info:    'bg-blue-950 text-blue-300 ring-blue-700',
};

/**
 * Reusable badge / pill component.
 */
export function Badge({ variant = 'neutral', children, className = '' }: BadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center rounded-full px-2 py-0.5',
        'text-xs font-medium ring-1 ring-inset',
        variantClasses[variant],
        className,
      ].join(' ')}
    >
      {children}
    </span>
  );
}
