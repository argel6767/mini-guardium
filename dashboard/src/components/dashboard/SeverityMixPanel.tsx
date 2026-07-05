import { ErrorState, LoadingState } from '@/components/dashboard/DataState'
import { severityRows } from '@/components/dashboard/severityRows'
import { useAlertSummary } from '@/hooks/useAlerts'

export function SeverityMixPanel() {
  const summaryQuery = useAlertSummary()
  const totalAlerts = summaryQuery.data?.totalAlerts ?? 0

  return (
    <aside className="panel-shadow rounded-md border border-white/10 bg-card/95 p-5">
      <h2 className="text-base font-semibold tracking-normal">Severity mix</h2>
      <div className="mt-5 grid gap-4">
        {summaryQuery.isLoading ? <LoadingState label="Loading severity mix" /> : null}
        {summaryQuery.isError ? (
          <ErrorState title="Unable to load severity mix" message={summaryQuery.error.message} />
        ) : null}
        {summaryQuery.isSuccess
          ? severityRows.map((row) => {
              const count = summaryQuery.data.countsBySeverity[row.severity]
              const width = totalAlerts > 0 ? (count / totalAlerts) * 100 : 0

              return <SeverityRow count={count} key={row.label} label={row.label} color={row.color} width={width} />
            })
          : null}
      </div>
    </aside>
  )
}

function SeverityRow({ label, count, color, width }: { label: string; count: number; color: string; width: number }) {
  return (
    <div>
      <div className="mb-2 flex items-center justify-between text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-medium">{count}</span>
      </div>
      <div className="h-2 rounded-full bg-muted" aria-label={`${label} severity count`}>
        <div className={`h-full rounded-full ${color}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}



