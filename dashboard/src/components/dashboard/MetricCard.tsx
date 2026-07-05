import type { ComponentType, ReactNode, SVGProps } from 'react'

export type MetricCardProps = {
  label: string
  value: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
  iconClassName: string
  children?: ReactNode
}

export function MetricCard({
  label,
  value,
  icon: Icon,
  iconClassName,
  children,
}: MetricCardProps) {
  return (
    <div className="panel-shadow rounded-md border border-white/10 bg-card/95 p-5">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        <Icon className={`size-4 ${iconClassName}`} aria-hidden="true" />
      </div>
      <p className="mt-3 text-3xl font-semibold tracking-normal">{value}</p>
      {children}
    </div>
  )
}

