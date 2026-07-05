import { Activity, Clock, ShieldAlert } from 'lucide-react'

import { MetricCard } from '@/components/dashboard/MetricCard'
import { useAlertSummary } from '@/hooks/useAlerts'

const liveRateBars = [38, 52, 31, 68, 47, 75, 59, 83, 64, 70]

export function MetricsGrid() {
  const summaryQuery = useAlertSummary()
  const summary = summaryQuery.data

  const totalAlerts = summaryQuery.isLoading
    ? '...'
    : summaryQuery.isError
      ? '!'
      : formatCount(summary?.totalAlerts ?? 0)
  const latestAlert = summaryQuery.isLoading
    ? '...'
    : summaryQuery.isError
      ? '!'
      : formatDateTime(summary?.latestAlertCreatedAt)

  return (
    <div className="grid gap-4 md:grid-cols-3">
      <MetricCard label="Total alerts" value={totalAlerts} icon={ShieldAlert} iconClassName="text-[var(--chart-critical)]">
        <div className="mt-4 h-1.5 rounded-full bg-muted" aria-label="Total alert activity">
          <div className="h-full w-2/3 rounded-full bg-[var(--chart-critical)]" />
        </div>
        {summaryQuery.isError ? <p className="mt-3 text-sm text-destructive">Unable to load summary</p> : null}
      </MetricCard>
      <MetricCard label="Live rate" value="--/min" icon={Activity} iconClassName="text-[var(--chart-medium)]">
        <div className="mt-4 flex h-8 items-end gap-1" aria-label="Live rate placeholder chart">
          {liveRateBars.map((height, index) => (
            <span
              className="w-full rounded-sm bg-[var(--chart-medium)]/75"
              style={{ height: `${height}%` }}
              key={`${height}-${index}`}
            />
          ))}
        </div>
      </MetricCard>
      <MetricCard label="Latest alert" value={latestAlert} icon={Clock} iconClassName="text-[var(--chart-low)]">
        <p className="mt-4 text-sm text-muted-foreground">
          {summaryQuery.isLoading
            ? 'Loading summary data'
            : summaryQuery.isError
              ? 'Summary request failed'
              : summary?.latestAlertCreatedAt
                ? 'Most recent evaluation alert'
                : 'No alerts recorded yet'}
        </p>
      </MetricCard>
    </div>
  )
}

function formatCount(value: number) {
  return new Intl.NumberFormat('en-US').format(value)
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'None'
  }

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
