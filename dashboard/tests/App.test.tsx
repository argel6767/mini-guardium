import { render, screen } from '@testing-library/react'

import App from '@/App'
import { useAlerts, useAlertSummary } from '@/hooks/useAlerts'

jest.mock('@/hooks/useAlerts', () => ({
  useAlerts: jest.fn(),
  useAlertSummary: jest.fn(),
}))

const mockedUseAlerts = jest.mocked(useAlerts)
const mockedUseAlertSummary = jest.mocked(useAlertSummary)

describe('App', () => {
  beforeEach(() => {
    mockedUseAlerts.mockReturnValue({
      data: {
        content: [],
      },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      isSuccess: true,
      refetch: jest.fn(),
    } as never)
    mockedUseAlertSummary.mockReturnValue({
      data: {
        totalAlerts: 0,
        latestAlertCreatedAt: null,
        countsBySeverity: {
          LOW: 0,
          MEDIUM: 0,
          HIGH: 0,
          CRITICAL: 0,
        },
      },
      error: null,
      isError: false,
      isLoading: false,
      isSuccess: true,
    } as never)
  })

  it('renders the dashboard shell sections', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'MiniGuardium' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Security telemetry' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Recent alerts' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Severity mix' })).toBeInTheDocument()
  })
})
