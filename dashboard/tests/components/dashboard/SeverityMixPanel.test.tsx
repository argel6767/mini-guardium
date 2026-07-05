import { render, screen } from '@testing-library/react'

import { SeverityMixPanel } from '@/components/dashboard/SeverityMixPanel'
import { severityRows } from '@/components/dashboard/severityRows'
import { useAlertSummary } from '@/hooks/useAlerts'

jest.mock('@/hooks/useAlerts', () => ({
  useAlertSummary: jest.fn(),
}))

const mockedUseAlertSummary = jest.mocked(useAlertSummary)

describe('SeverityMixPanel', () => {
  beforeEach(() => {
    mockedUseAlertSummary.mockReset()
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
    } as never)

    const { rerender } = render(<SeverityMixPanel />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading severity mix')

    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      error: new Error('Summary failed'),
      isError: true,
      isLoading: false,
      isSuccess: false,
    } as never)

    rerender(<SeverityMixPanel />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load severity mix')
    expect(screen.getByText('Summary failed')).toBeInTheDocument()
  })
})
