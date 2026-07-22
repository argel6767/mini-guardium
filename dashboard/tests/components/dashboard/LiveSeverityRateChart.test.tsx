import { render, screen } from '@testing-library/react'

import { LiveSeverityRateChart } from '@/components/dashboard/LiveSeverityRateChart'
import type { AlertRateSnapshot } from '@/lib/api'

describe('LiveSeverityRateChart', () => {
  it('renders an empty state and severity legend before snapshots arrive', () => {
    render(<LiveSeverityRateChart snapshots={[]} />)

    expect(screen.getByText('Waiting for live rate data')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText('Critical')).toBeInTheDocument()
    expect(screen.getByText('High')).toBeInTheDocument()
    expect(screen.getByText('Medium')).toBeInTheDocument()
    expect(screen.getByText('Low')).toBeInTheDocument()
  })

  it('draws a full-width horizontal line for a single snapshot', () => {
    const { container } = render(
      <LiveSeverityRateChart snapshots={[snapshot({ CRITICAL: 2 })]} />,
    )

    expect(screen.getByRole('img')).toHaveAccessibleName('Average alerts per minute by severity')
    expect(container.querySelectorAll('polyline')[0])
      .toHaveAttribute('points', '14,14 346,14')
  })

  it('scales multiple snapshots against the largest severity rate', () => {
    const { container } = render(
      <LiveSeverityRateChart snapshots={[
        snapshot({ CRITICAL: 0 }),
        snapshot({ CRITICAL: 2 }),
      ]} />,
    )

    expect(container.querySelectorAll('polyline')[0])
      .toHaveAttribute('points', '14.00,114.00 346.00,14.00')
    expect(container.querySelectorAll('polyline')).toHaveLength(4)
  })
})

function snapshot(bySeverity: Partial<AlertRateSnapshot['bySeverityPerMinute']>): AlertRateSnapshot {
  return {
    timestamp: '2026-07-21T12:00:00Z',
    windowSeconds: 60,
    intervalSeconds: 5,
    overallPerMinute: 2,
    bySeverityPerMinute: {
      LOW: 0,
      MEDIUM: 0,
      HIGH: 0,
      CRITICAL: 0,
      ...bySeverity,
    },
    byRulePerMinute: {},
  }
}

