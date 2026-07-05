import { render, screen } from '@testing-library/react'

import { MetricsGrid } from '@/components/dashboard/MetricsGrid'
import { useAlertSummary } from '@/hooks/useAlerts'

jest.mock('@/hooks/useAlerts', () => ({
  useAlertSummary: jest.fn(),
}))

const mockedUseAlertSummary = jest.mocked(useAlertSummary)

describe('MetricsGrid', () => {
  beforeEach(() => {
    mockedUseAlertSummary.mockReset()
  })

  it('renders summary-backed metric cards', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: {
        totalAlerts: 42,
        latestAlertCreatedAt: '2026-07-02T11:01:00Z',
        countsBySeverity: {
          LOW: 1,
          MEDIUM: 2,
          HIGH: 3,
          CRITICAL: 4,
        },
      },
      isError: false,
      isLoading: false,
    } as never)

    render(<MetricsGrid />)

    expect(screen.getByText('Total alerts')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText('Live rate')).toBeInTheDocument()
    expect(screen.getByText('--/min')).toBeInTheDocument()
    expect(screen.getByText('Latest alert')).toBeInTheDocument()
    expect(screen.getByText('Most recent evaluation alert')).toBeInTheDocument()
  })

  it('renders loading state for summary-backed metrics', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: true,
    } as never)

    render(<MetricsGrid />)

    expect(screen.getAllByText('...')).toHaveLength(2)
    expect(screen.getByText('Loading summary data')).toBeInTheDocument()
  })

  it('renders error state for summary-backed metrics', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
    } as never)

    render(<MetricsGrid />)

    expect(screen.getByText('Unable to load summary')).toBeInTheDocument()
    expect(screen.getByText('Summary request failed')).toBeInTheDocument()
  })

  it('renders placeholder chart regions for alert and rate data', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: true,
    } as never)

    render(<MetricsGrid />)

    expect(screen.getByLabelText('Total alert activity')).toBeInTheDocument()
    expect(screen.getByLabelText('Live rate placeholder chart')).toBeInTheDocument()
  })
})
