'use client';

import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { Card } from '@/components/ui/Card';
import type { SentimentAlert } from '@/types/market';

interface SentimentChartProps {
  alerts: SentimentAlert[];
}

/**
 * Line chart visualising sentiment scores over time.
 * Renders the last N data points from the incoming alert stream.
 */
export function SentimentChart({ alerts }: SentimentChartProps) {
  const MAX_POINTS = 60;
  const recent = alerts.slice(0, MAX_POINTS).reverse();

  const timestamps = recent.map((a) =>
    new Date(a.processedAtMs).toLocaleTimeString(),
  );
  const scores = recent.map((a) => a.score);

  const option: EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      axisLine: { lineStyle: { color: '#334155' } },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 1,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1e293b' } },
    },
    series: [
      {
        name: 'Sentiment score',
        type: 'line',
        smooth: true,
        data: scores,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(16,185,129,0.25)' },
              { offset: 1, color: 'rgba(16,185,129,0)' },
            ],
          },
        },
        symbol: 'none',
      },
    ],
  };

  return (
    <Card title="Sentiment Over Time">
      <ReactECharts
        option={option}
        style={{ height: 300, width: '100%' }}
        notMerge
        lazyUpdate
      />
    </Card>
  );
}
