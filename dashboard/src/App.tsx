import { DashboardHeader } from '@/components/dashboard/DashboardHeader'
import { MetricsGrid } from '@/components/dashboard/MetricsGrid'
import { OverviewHeader } from '@/components/dashboard/OverviewHeader'
import { RecentAlertsPanel } from '@/components/dashboard/RecentAlertsPanel'
import { SeverityMixPanel } from '@/components/dashboard/SeverityMixPanel'

function App() {
  return (
    <main className="dashboard-grid min-h-svh bg-background text-foreground">
      <DashboardHeader />

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6">
        <OverviewHeader />
        <MetricsGrid />

        <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
          <RecentAlertsPanel />
          <SeverityMixPanel />
        </div>
      </section>
    </main>
  )
}

export default App
