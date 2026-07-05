import { severityRows } from '@/components/dashboard/severityRows'

export function SeverityMixPanel() {
  return (
    <aside className="panel-shadow rounded-md border border-white/10 bg-card/95 p-5">
      <h2 className="text-base font-semibold tracking-normal">Severity mix</h2>
      <div className="mt-5 grid gap-4">
        {severityRows.map((row) => (
          <div key={row.label}>
            <div className="mb-2 flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{row.label}</span>
              <span className="font-medium">--</span>
            </div>
            <div className="h-2 rounded-full bg-muted" aria-label={`${row.label} severity placeholder`}>
              <div className={`h-full rounded-full ${row.color}`} style={{ width: `${row.value}%` }} />
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}
