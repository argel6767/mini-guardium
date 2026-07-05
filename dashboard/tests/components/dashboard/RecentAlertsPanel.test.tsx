import { render, screen } from '@testing-library/react'

import { RecentAlertsPanel } from '@/components/dashboard/RecentAlertsPanel'

describe('RecentAlertsPanel', () => {
  it('renders the recent-alerts empty state and refresh action', () => {
    render(<RecentAlertsPanel />)

    expect(screen.getByRole('heading', { name: 'Recent alerts' })).toBeInTheDocument()
    expect(screen.getByText('Ready to connect to the evaluation API.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByText('GET /alerts')).toBeInTheDocument()
  })
})
