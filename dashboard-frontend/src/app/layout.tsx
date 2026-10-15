import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'InsightPulse',
  description: 'Real-time market intelligence dashboard',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-primary-950 text-primary-100 antialiased">
        {children}
      </body>
    </html>
  );
}
