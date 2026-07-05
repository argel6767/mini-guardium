import { render, screen } from '@testing-library/react'

import { DashboardHeader } from '@/components/dashboard/DashboardHeader'

describe('DashboardHeader', () => {
  it('shows the product identity and alert action', () => {
    render(<DashboardHeader />)

    expect(screen.getByRole('heading', { name: 'MiniGuardium' })).toBeInTheDocument()
    expect(screen.getByText('Database activity monitor')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Alerts' })).toBeInTheDocument()
  })
})
