import { render, screen } from '@testing-library/react'

import App from '@/App'

describe('App', () => {
  it('renders the dashboard shell sections', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'MiniGuardium' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Security telemetry' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Recent alerts' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Severity mix' })).toBeInTheDocument()
  })
})
