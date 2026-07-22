import { render, screen } from '@testing-library/react'

import { ErrorState, LoadingState } from '@/components/dashboard/DataState'

describe('DataState', () => {
  it('renders an accessible loading status', () => {
    render(<LoadingState label="Loading alerts" />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading alerts')
  })

  it('renders an accessible error with optional detail', () => {
    const { rerender } = render(<ErrorState title="Unable to load alerts" message="Request timed out" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load alerts')
    expect(screen.getByRole('alert')).toHaveTextContent('Request timed out')

    rerender(<ErrorState title="Unable to load alerts" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load alerts')
    expect(screen.queryByText('Request timed out')).not.toBeInTheDocument()
  })
})
