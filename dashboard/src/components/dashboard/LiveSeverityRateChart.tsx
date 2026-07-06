import type { AlertRateSnapshot, AlertSeverity } from '@/lib/api'

const chartWidth = 360
const chartHeight = 128
const chartPadding = 14

const severitySeries: Array<{ severity: AlertSeverity; label: string; className: string }> = [
  { severity: 'CRITICAL', label: 'Critical', className: 'text-[var(--chart-critical)]' },
  { severity: 'HIGH', label: 'High', className: 'text-[var(--chart-high)]' },
  { severity: 'MEDIUM', label: 'Medium', className: 'text-[var(--chart-medium)]' },
  { severity: 'LOW', label: 'Low', className: 'text-[var(--chart-low)]' },
]

type LiveSeverityRateChartProps = {
  snapshots: AlertRateSnapshot[]
}

export function LiveSeverityRateChart({ snapshots }: LiveSeverityRateChartProps) {
  const maxRate = Math.max(
    1,
    ...snapshots.flatMap((snapshot) => severitySeries.map((series) => snapshot.bySeverityPerMinute[series.severity] ?? 0)),
  )

  return (
    <div className="mt-4" aria-label="Average alerts per minute by severity">
      <div className="h-32 overflow-hidden rounded-md border border-white/10 bg-black/20">
        {snapshots.length > 0 ? (
          <svg className="h-full w-full" viewBox={`0 0 ${chartWidth} ${chartHeight}`} role="img">
            <title>Average alerts per minute by severity</title>
            <g className="text-white/10" stroke="currentColor" strokeWidth="1">
              {[0.25, 0.5, 0.75].map((tick) => (
                <line
                  key={tick}
                  x1={chartPadding}
                  x2={chartWidth - chartPadding}
                  y1={chartPadding + (chartHeight - chartPadding * 2) * tick}
                  y2={chartPadding + (chartHeight - chartPadding * 2) * tick}
                />
              ))}
            </g>
            {severitySeries.map((series) => (
              <polyline
                className={series.className}
                fill="none"
                key={series.severity}
                points={toPoints(snapshots, series.severity, maxRate)}
                stroke="currentColor"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2.25"
              />
            ))}
          </svg>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted-foreground">Waiting for live rate data</div>
        )}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-muted-foreground sm:grid-cols-4">
        {severitySeries.map((series) => (
          <div className="flex items-center gap-2" key={series.severity}>
            <span className={`size-2 rounded-full bg-current ${series.className}`} aria-hidden="true" />
            <span>{series.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function toPoints(snapshots: AlertRateSnapshot[], severity: AlertSeverity, maxRate: number) {
  if (snapshots.length === 1) {
    const y = toY(snapshots[0].bySeverityPerMinute[severity] ?? 0, maxRate)
    return `${chartPadding},${y} ${chartWidth - chartPadding},${y}`
  }

  return snapshots
    .map((snapshot, index) => {
      const x = chartPadding + (index / (snapshots.length - 1)) * (chartWidth - chartPadding * 2)
      const y = toY(snapshot.bySeverityPerMinute[severity] ?? 0, maxRate)

      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
}

function toY(value: number, maxRate: number) {
  const plotHeight = chartHeight - chartPadding * 2
  const normalizedValue = Math.min(value / maxRate, 1)

  return chartHeight - chartPadding - normalizedValue * plotHeight
}