import { useCallback, useState } from 'react'
import { Activity, Clock, ShieldAlert } from 'lucide-react'

import { LiveSeverityRateChart } from '@/components/dashboard/LiveSeverityRateChart'
import { MetricCard } from '@/components/dashboard/MetricCard'
import { useAlertSummary } from '@/hooks/useAlerts'
import { useAlertBatchStream, useAlertRateStream } from '@/hooks/useAlertStreams'
import type { Alert, AlertBatch, AlertRateSnapshot } from '@/lib/api'

const maxRateHistoryLength = 12

export function MetricsGrid() {
  const summaryQuery = useAlertSummary()
  const summary = summaryQuery.data
  const [rateSnapshot, setRateSnapshot] = useState<AlertRateSnapshot | null>(null)
  const [rateHistory, setRateHistory] = useState<AlertRateSnapshot[]>([])
  const [latestLiveAlert, setLatestLiveAlert] = useState<Alert | null>(null)

  const handleRateSnapshot = useCallback((snapshot: AlertRateSnapshot) => {
    setRateSnapshot(snapshot)
    setRateHistory((currentHistory) => [...currentHistory, snapshot].slice(-maxRateHistoryLength))
  }, [])

  const refetchSummary = summaryQuery.refetch
  const handleAlertBatch = useCallback(
    (batch: AlertBatch) => {
      const latestAlert = findLatestAlert(batch.alerts)

      if (latestAlert) {
        setLatestLiveAlert(latestAlert)
      }

      void refetchSummary()
    },
    [refetchSummary],
  )

  useAlertRateStream({ onMessage: handleRateSnapshot })
  useAlertBatchStream({ onMessage: handleAlertBatch })

  const totalAlerts = summaryQuery.isLoading
    ? '...'
    : summaryQuery.isError
      ? '!'
      : formatCount(summary?.totalAlerts ?? 0)
  const liveRate = rateSnapshot ? `${formatRate(rateSnapshot.overallPerMinute)}/min` : '--/min'
  const latestAlertTimestamp = latestLiveAlert?.createdAt ?? summary?.latestAlertCreatedAt
  const latestAlert = summaryQuery.isLoading && !latestLiveAlert
    ? '...'
    : summaryQuery.isError && !latestLiveAlert
      ? '!'
      : formatDateTime(latestAlertTimestamp)

  return (
    <div className="grid gap-4 md:grid-cols-3">
      <MetricCard label="Total alerts" value={totalAlerts} icon={ShieldAlert} iconClassName="text-[var(--chart-critical)]">
        <div className="mt-4 h-1.5 rounded-full bg-muted" aria-label="Total alert activity">
          <div className="h-full w-2/3 rounded-full bg-[var(--chart-critical)]" />
        </div>
        {summaryQuery.isError ? <p className="mt-3 text-sm text-destructive">Unable to load summary</p> : null}
      </MetricCard>
      <MetricCard label="Live rate" value={liveRate} icon={Activity} iconClassName="text-[var(--chart-medium)]">
        <LiveSeverityRateChart snapshots={rateHistory} />
      </MetricCard>
      <MetricCard label="Latest alert" value={latestAlert} icon={Clock} iconClassName="text-[var(--chart-low)]">
        <p className="mt-4 text-sm text-muted-foreground">
          {latestLiveAlert
            ? `${latestLiveAlert.severity} ${latestLiveAlert.ruleName}`
            : summaryQuery.isLoading
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

function findLatestAlert(alerts: Alert[]) {
  return alerts.reduce<Alert | null>((latestAlert, alert) => {
    if (!latestAlert || Date.parse(alert.createdAt) > Date.parse(latestAlert.createdAt)) {
      return alert
    }

    return latestAlert
  }, null)
}

function formatCount(value: number) {
  return new Intl.NumberFormat('en-US').format(value)
}

function formatRate(value: number) {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: value >= 10 ? 0 : 1,
  }).format(value)
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