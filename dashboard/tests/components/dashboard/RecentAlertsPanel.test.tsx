import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { RecentAlertsPanel } from '@/components/dashboard/RecentAlertsPanel'
import { useAlerts } from '@/hooks/useAlerts'

jest.mock('@/hooks/useAlerts', () => ({
  useAlerts: jest.fn(),
}))

const mockedUseAlerts = jest.mocked(useAlerts)

describe('RecentAlertsPanel', () => {
  beforeEach(() => {
    mockedUseAlerts.mockReset()
  })

  it('renders recent alerts from the alert list query', () => {
    mockedUseAlerts.mockReturnValue({
      data: {
        content: [
          {
            id: 12,
            ruleName: 'ACCESS_EVENT_RISK',
            severity: 'HIGH',
            message: 'alice accessed customer_accounts',
            createdAt: '2026-07-02T11:01:00Z',
            accessEvent: {
              id: 20,
              username: 'alice',
              tableName: 'customer_accounts',
              queryType: 'SELECT',
              occurredAt: '2026-07-02T11:00:00Z',
              rowCount: 42,
              sourceIp: '10.0.0.12',
            },
          },
        ],
      },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      isSuccess: true,
      refetch: jest.fn(),
    } as never)

    render(<RecentAlertsPanel />)

    expect(screen.getByRole('heading', { name: 'Recent alerts' })).toBeInTheDocument()
    expect(screen.getByText('Latest alerts from the evaluation service.')).toBeInTheDocument()
    expect(screen.getByText('ACCESS_EVENT_RISK')).toBeInTheDocument()
    expect(screen.getByText('alice accessed customer_accounts')).toBeInTheDocument()
    expect(screen.getByText('alice')).toBeInTheDocument()
  })

  it('renders loading and empty states', () => {
    mockedUseAlerts.mockReturnValue({
      data: undefined,
      error: null,
      isError: false,
      isFetching: false,
      isLoading: true,
      isSuccess: false,
      refetch: jest.fn(),
    } as never)

    const { rerender } = render(<RecentAlertsPanel />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading recent alerts')

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

    rerender(<RecentAlertsPanel />)

    expect(screen.getByText('No alerts have been created yet.')).toBeInTheDocument()
  })

  it('renders error state and refreshes on command', async () => {
    const user = userEvent.setup()
    const refetch = jest.fn()
    mockedUseAlerts.mockReturnValue({
      data: undefined,
      error: new Error('Request failed'),
      isError: true,
      isFetching: false,
      isLoading: false,
      isSuccess: false,
      refetch,
    } as never)

    render(<RecentAlertsPanel />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load recent alerts')
    expect(screen.getByText('Request failed')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(refetch).toHaveBeenCalledTimes(1)
  })
})
