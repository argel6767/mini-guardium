import { RefreshCw } from 'lucide-react'

import { Button } from '@/components/ui/button'

export function RecentAlertsPanel() {
  return (
    <section className="panel-shadow rounded-md border border-white/10 bg-card/95 p-5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-normal">Recent alerts</h2>
          <p className="mt-1 text-sm text-muted-foreground">Ready to connect to the evaluation API.</p>
        </div>
        <Button variant="outline" size="sm">
          <RefreshCw className="size-4" aria-hidden="true" />
          Refresh
        </Button>
      </div>
      <div className="mt-6 rounded-md border border-dashed border-white/15 bg-black/20 p-8 text-center text-sm text-muted-foreground">
        Connect this view to <code className="rounded bg-muted px-1.5 py-0.5 text-foreground">GET /alerts</code>.
      </div>
    </section>
  )
}
