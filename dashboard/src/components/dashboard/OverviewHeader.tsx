export function OverviewHeader() {
  return (
    <div className="flex flex-col gap-3 border-b border-white/10 pb-5 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-xs font-medium uppercase tracking-[0.18em] text-primary">Overview</p>
        <h2 className="mt-2 text-2xl font-semibold tracking-normal">Security telemetry</h2>
      </div>
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <span
          className="size-2 rounded-full bg-[var(--chart-low)] shadow-[0_0_16px_var(--chart-low)]"
          aria-hidden="true"
        />
        Live stream standby
      </div>
    </div>
  )
}
