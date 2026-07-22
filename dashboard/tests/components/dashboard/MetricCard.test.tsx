import { render, screen } from '@testing-library/react'
import type { SVGProps } from 'react'

import { MetricCard } from '@/components/dashboard/MetricCard'

function TestIcon(props: SVGProps<SVGSVGElement>) {
  return <svg data-testid="metric-icon" {...props} />
}

describe('MetricCard', () => {
  it('renders its label, value, icon styling, and supporting content', () => {
    render(
      <MetricCard label="Total alerts" value="42" icon={TestIcon} iconClassName="text-red-500">
        <span>Updated live</span>
      </MetricCard>,
    )

    expect(screen.getByText('Total alerts')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText('Updated live')).toBeInTheDocument()
    expect(screen.getByTestId('metric-icon')).toHaveClass('size-4', 'text-red-500')
    expect(screen.getByTestId('metric-icon')).toHaveAttribute('aria-hidden', 'true')
  })
})

