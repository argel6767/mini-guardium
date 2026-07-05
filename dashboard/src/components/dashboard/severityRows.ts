import type { AlertSeverity } from '@/lib/api'

export const severityRows: Array<{ label: string; severity: AlertSeverity; color: string }> = [
  { label: 'Critical', severity: 'CRITICAL', color: 'bg-[var(--chart-critical)]' },
  { label: 'High', severity: 'HIGH', color: 'bg-[var(--chart-high)]' },
  { label: 'Medium', severity: 'MEDIUM', color: 'bg-[var(--chart-medium)]' },
  { label: 'Low', severity: 'LOW', color: 'bg-[var(--chart-low)]' },
]
