import { render, screen } from '@testing-library/react'

import { SeverityMixPanel } from '@/components/dashboard/SeverityMixPanel'
import { severityRows } from '@/components/dashboard/severityRows'
import { useAlertSummary } from '@/hooks/useAlerts'
import { useAlertSeverityStream } from '@/hooks/useAlertStreams'

jest.mock('@/hooks/useAlerts', () => ({
  useAlertSummary: jest.fn(),
}))

jest.mock('@/hooks/useAlertStreams', () => ({
  useAlertSeverityStream: jest.fn(),
}))

const mockedUseAlertSummary = jest.mocked(useAlertSummary)
const mockedUseAlertSeverityStream = jest.mocked(useAlertSeverityStream)

describe('SeverityMixPanel', () => {
  beforeEach(() => {
    mockedUseAlertSummary.mockReset()
    mockedUseAlertSeverityStream.mockReset()
  })

  it('renders every configured severity row with summary counts', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: {
        totalAlerts: 10,
        latestAlertCreatedAt: '2026-07-02T11:01:00Z',
        countsBySeverity: {
          LOW: 1,
          MEDIUM: 2,
          HIGH: 3,
          CRITICAL: 4,
        },
      },
      error: null,
      isError: false,
      isLoading: false,
      isSuccess: true,
      refetch: jest.fn(),
    } as never)

    render(<SeverityMixPanel />)

    expect(screen.getByRole('heading', { name: 'Severity mix' })).toBeInTheDocument()

    for (const row of severityRows) {
      expect(screen.getByText(row.label)).toBeInTheDocument()
      expect(screen.getByLabelText(`${row.label} severity count`)).toBeInTheDocument()
    }

    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('renders loading and error states', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      error: null,
      isError: false,
      isLoading: true,
      isSuccess: false,
      refetch: jest.fn(),
    } as never)

    const { rerender } = render(<SeverityMixPanel />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading severity mix')

    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      error: new Error('Summary failed'),
      isError: true,
      isLoading: false,
      isSuccess: false,
      refetch: jest.fn(),
    } as never)

    rerender(<SeverityMixPanel />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load severity mix')
    expect(screen.getByText('Summary failed')).toBeInTheDocument()
  })
  it('refreshes the summary when a live severity event arrives', () => {
    const refetch = jest.fn()
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
      refetch,
    } as never)

    render(<SeverityMixPanel />)

    mockedUseAlertSeverityStream.mock.calls[0][0].onMessage({
      alertId: 7,
      severity: 'HIGH',
      ruleName: 'ACCESS_EVENT_RISK',
      createdAt: '2026-07-02T11:02:00Z',
    })

    expect(refetch).toHaveBeenCalledTimes(1)
  })
})