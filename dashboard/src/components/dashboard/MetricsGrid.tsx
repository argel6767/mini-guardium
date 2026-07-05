import { Activity, Database, ShieldAlert } from 'lucide-react'

import { MetricCard } from '@/components/dashboard/MetricCard'

const liveRateBars = [38, 52, 31, 68, 47, 75, 59, 83, 64, 70]

export function MetricsGrid() {
  return (
    <div className="grid gap-4 md:grid-cols-3">
      <MetricCard label="Total alerts" value="--" icon={ShieldAlert} iconClassName="text-[var(--chart-critical)]">
        <div className="mt-4 h-1.5 rounded-full bg-muted" aria-label="Total alert placeholder progress">
          <div className="h-full w-2/3 rounded-full bg-[var(--chart-critical)]" />
        </div>
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
      <MetricCard label="Tracked tables" value="--" icon={Database} iconClassName="text-[var(--chart-low)]">
        <p className="mt-4 text-sm text-muted-foreground">Waiting for summary data</p>
      </MetricCard>
    </div>
  )
}
