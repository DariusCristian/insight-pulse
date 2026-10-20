'use client';

import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { Card } from '@/components/ui/Card';
import type { SentimentAlert } from '@/types/market';

interface SentimentChartProps {
  alerts: SentimentAlert[];
}

/** Scores above / below these thresholds are colour-coded bullish / bearish. */
export const BULLISH_THRESHOLD = 0.5;
export const BEARISH_THRESHOLD = -0.5;

// Tailwind palette (tailwind.config.ts): accent-500, danger-500, primary-400.
const GREEN = '#10b981';
const RED = '#ef4444';
const NEUTRAL = '#94a3b8';

/** Colour for a score: green (> 0.5), red (< -0.5), neutral otherwise. */
export function scoreColor(score: number): string {
  if (score > BULLISH_THRESHOLD) return GREEN;
  if (score < BEARISH_THRESHOLD) return RED;
  return NEUTRAL;
}

/**
 * Line chart of sentiment scores over time on a fixed [-1, 1] axis.
 * Line segments and points are coloured green (> 0.5), red (< -0.5) or
 * neutral, with dashed guide lines at the two thresholds.
 */
export function SentimentChart({ alerts }: SentimentChartProps) {
  // `alerts` is newest-first; the chart reads left (old) to right (new).
  const recent = [...alerts].reverse();

  const timestamps = recent.map((a) =>
    new Date(a.processedAtMs).toLocaleTimeString(),
  );

  const option: EChartsOption = {
    backgroundColor: 'transparent',
    animationDurationUpdate: 300,
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = Array.isArray(params) ? params[0] : params;
        const alert = recent[p.dataIndex];
        if (!alert) return '';
        return `${alert.assetSymbol} · ${alert.label}<br/>` +
          `Score: <b style="color:${scoreColor(alert.score)}">${alert.score.toFixed(3)}</b><br/>` +
          `${new Date(alert.processedAtMs).toLocaleTimeString()}`;
      },
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: timestamps,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      axisLine: { lineStyle: { color: '#334155' } },
    },
    yAxis: {
      type: 'value',
      min: -1,
      max: 1,
      interval: 0.5,
      axisLabel: { color: '#94a3b8', fontSize: 11 },
      splitLine: { lineStyle: { color: '#1e293b' } },
    },
    // Colours the line by value; `show: false` hides the legend widget.
    visualMap: {
      show: false,
      seriesIndex: 0,
      dimension: 1,
      pieces: [
        { gt: BULLISH_THRESHOLD, color: GREEN },
        { lt: BEARISH_THRESHOLD, color: RED },
        { gte: BEARISH_THRESHOLD, lte: BULLISH_THRESHOLD, color: NEUTRAL },
      ],
    },
    series: [
      {
        name: 'Sentiment score',
        type: 'line',
        smooth: true,
        lineStyle: { width: 2 },
        symbol: 'circle',
        symbolSize: 6,
        data: recent.map((a) => ({
          value: a.score,
          itemStyle: { color: scoreColor(a.score) },
        })),
        markLine: {
          silent: true,
          symbol: 'none',
          label: { show: false },
          data: [
            { yAxis: BULLISH_THRESHOLD, lineStyle: { color: GREEN, type: 'dashed', opacity: 0.5 } },
            { yAxis: 0, lineStyle: { color: '#334155', type: 'solid' } },
            { yAxis: BEARISH_THRESHOLD, lineStyle: { color: RED, type: 'dashed', opacity: 0.5 } },
          ],
        },
      },
    ],
  };

  return (
    <Card title="Sentiment Over Time">
      {alerts.length === 0 ? (
        <p className="flex h-[300px] items-center justify-center text-sm text-primary-500">
          Waiting for sentiment data…
        </p>
      ) : (
        <ReactECharts
          option={option}
          style={{ height: 300, width: '100%' }}
          notMerge
          lazyUpdate
        />
      )}
    </Card>
  );
}
