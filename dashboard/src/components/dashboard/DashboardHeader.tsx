import { Bell, ShieldAlert } from 'lucide-react'

import { Button } from '@/components/ui/button'

export function DashboardHeader() {
  return (
    <header className="border-b border-white/10 bg-[#18191f]/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-md border border-primary/30 bg-primary/15 text-primary shadow-[0_0_24px_oklch(0.69_0.17_45_/_18%)]">
            <ShieldAlert className="size-5" aria-hidden="true" />
          </div>
          <div>
            <h1 className="text-lg font-semibold leading-none tracking-normal">MiniGuardium</h1>
            <p className="mt-1 text-sm text-muted-foreground">Database activity monitor</p>
          </div>
        </div>
        <Button size="sm">
          <Bell className="size-4" aria-hidden="true" />
          Alerts
        </Button>
      </div>
    </header>
  )
}
