import { render, screen } from '@testing-library/react'

import { OverviewHeader } from '@/components/dashboard/OverviewHeader'

describe('OverviewHeader', () => {
  it('describes the dashboard overview and stream state', () => {
    render(<OverviewHeader />)

    expect(screen.getByText('Overview')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Security telemetry' })).toBeInTheDocument()
    expect(screen.getByText('Live stream standby')).toBeInTheDocument()
  })
})
