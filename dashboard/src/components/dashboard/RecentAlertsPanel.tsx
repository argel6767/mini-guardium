import { RefreshCw } from 'lucide-react'

import { ErrorState, LoadingState } from '@/components/dashboard/DataState'
import { Button } from '@/components/ui/button'
import { useAlerts } from '@/hooks/useAlerts'
import type { Alert } from '@/lib/api'

const recentAlertParams = {
  page: 0,
  size: 5,
  sort: ['createdAt,desc', 'id,desc'],
}

export function RecentAlertsPanel() {
  const alertsQuery = useAlerts(recentAlertParams)
  const alerts = alertsQuery.data?.content ?? []

  return (
    <section className="panel-shadow rounded-md border border-white/10 bg-card/95 p-5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-normal">Recent alerts</h2>
          <p className="mt-1 text-sm text-muted-foreground">Latest alerts from the evaluation service.</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => void alertsQuery.refetch()} disabled={alertsQuery.isFetching}>
          <RefreshCw className="size-4" aria-hidden="true" />
          Refresh
        </Button>
      </div>

      <div className="mt-6">
        {alertsQuery.isLoading ? <LoadingState label="Loading recent alerts" /> : null}
        {alertsQuery.isError ? (
          <ErrorState title="Unable to load recent alerts" message={alertsQuery.error.message} />
        ) : null}
        {alertsQuery.isSuccess && alerts.length === 0 ? (
          <div className="rounded-md border border-dashed border-white/15 bg-black/20 p-8 text-center text-sm text-muted-foreground">
            No alerts have been created yet.
          </div>
        ) : null}
        {alerts.length > 0 ? <RecentAlertList alerts={alerts} /> : null}
      </div>
    </section>
  )
}

function RecentAlertList({ alerts }: { alerts: Alert[] }) {
  return (
    <div className="overflow-hidden rounded-md border border-white/10">
      <div className="grid grid-cols-[110px_1fr_130px] gap-3 border-b border-white/10 bg-black/20 px-4 py-2 text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">
        <span>Severity</span>
        <span>Rule</span>
        <span>User</span>
      </div>
      <div className="divide-y divide-white/10">
        {alerts.map((alert) => (
          <article className="grid grid-cols-[110px_1fr_130px] gap-3 px-4 py-3 text-sm" key={alert.id}>
            <span className="font-medium text-foreground">{alert.severity}</span>
            <div>
              <p className="font-medium text-foreground">{alert.ruleName}</p>
              <p className="mt-1 truncate text-muted-foreground">{alert.message}</p>
            </div>
            <span className="truncate text-muted-foreground">{alert.accessEvent.username}</span>
          </article>
        ))}
      </div>
    </div>
  )
}
