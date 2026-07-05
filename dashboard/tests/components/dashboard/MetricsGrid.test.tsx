import { render, screen } from '@testing-library/react'

import { MetricsGrid } from '@/components/dashboard/MetricsGrid'

describe('MetricsGrid', () => {
  it('renders the initial metric cards', () => {
    render(<MetricsGrid />)

    expect(screen.getByText('Total alerts')).toBeInTheDocument()
    expect(screen.getByText('Live rate')).toBeInTheDocument()
    expect(screen.getByText('Tracked tables')).toBeInTheDocument()
    expect(screen.getByText('--/min')).toBeInTheDocument()
    expect(screen.getByText('Waiting for summary data')).toBeInTheDocument()
  })

  it('renders placeholder chart regions for alert and rate data', () => {
    render(<MetricsGrid />)

    expect(screen.getByLabelText('Total alert placeholder progress')).toBeInTheDocument()
    expect(screen.getByLabelText('Live rate placeholder chart')).toBeInTheDocument()
  })
})
