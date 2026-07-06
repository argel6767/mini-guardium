import { act, render, screen } from '@testing-library/react'

import { MetricsGrid } from '@/components/dashboard/MetricsGrid'
import { useAlertSummary } from '@/hooks/useAlerts'
import { useAlertBatchStream, useAlertRateStream } from '@/hooks/useAlertStreams'
import type { Alert, AlertBatch, AlertRateSnapshot } from '@/lib/api'

jest.mock('@/hooks/useAlerts', () => ({
  useAlertSummary: jest.fn(),
}))

jest.mock('@/hooks/useAlertStreams', () => ({
  useAlertBatchStream: jest.fn(),
  useAlertRateStream: jest.fn(),
}))

const mockedUseAlertSummary = jest.mocked(useAlertSummary)
const mockedUseAlertBatchStream = jest.mocked(useAlertBatchStream)
const mockedUseAlertRateStream = jest.mocked(useAlertRateStream)

const summaryQuery = {
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
  error: null,
  isError: false,
  isLoading: false,
  refetch: jest.fn(),
}

describe('MetricsGrid', () => {
  beforeEach(() => {
    mockedUseAlertSummary.mockReset()
    mockedUseAlertBatchStream.mockReset()
    mockedUseAlertRateStream.mockReset()
    summaryQuery.refetch.mockReset()
    mockedUseAlertSummary.mockReturnValue(summaryQuery as never)
  })

  it('renders summary-backed metric cards', () => {
    render(<MetricsGrid />)

    expect(screen.getByText('Total alerts')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText('Live rate')).toBeInTheDocument()
    expect(screen.getByText('--/min')).toBeInTheDocument()
    expect(screen.getByText('Latest alert')).toBeInTheDocument()
    expect(screen.getByText('Most recent evaluation alert')).toBeInTheDocument()
  })

  it('renders live rate snapshots and the severity rate chart from SSE', () => {
    render(<MetricsGrid />)

    act(() => {
      mockedUseAlertRateStream.mock.calls[0][0].onMessage(rateSnapshot({ overallPerMinute: 14.8, high: 5, medium: 7 }))
    })

    expect(screen.getByText('15/min')).toBeInTheDocument()
    expect(screen.getByLabelText('Average alerts per minute by severity')).toBeInTheDocument()
    expect(screen.getByText('Critical')).toBeInTheDocument()
    expect(screen.getByText('High')).toBeInTheDocument()
    expect(screen.getByText('Medium')).toBeInTheDocument()
    expect(screen.getByText('Low')).toBeInTheDocument()
  })

  it('updates the latest alert card from the newest parsed alert timestamp in batch SSE', () => {
    render(<MetricsGrid />)

    act(() => {
      mockedUseAlertBatchStream.mock.calls[0][0].onMessage({
        alerts: [
          alert({ id: 1, severity: 'LOW', createdAt: '2026-07-02T11:03:00Z' }),
          alert({ id: 2, severity: 'HIGH', createdAt: '2026-07-02T11:03:00.100Z' }),
        ],
        batchSize: 2,
        sentAt: '2026-07-02T11:03:01Z',
      } satisfies AlertBatch)
    })

    expect(screen.getByText('HIGH ACCESS_EVENT_RISK')).toBeInTheDocument()
    expect(summaryQuery.refetch).toHaveBeenCalledTimes(1)
  })

  it('renders loading state for summary-backed metrics', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      error: null,
      isError: false,
      isLoading: true,
      refetch: jest.fn(),
    } as never)

    render(<MetricsGrid />)

    expect(screen.getAllByText('...')).toHaveLength(2)
    expect(screen.getByText('Loading summary data')).toBeInTheDocument()
  })

  it('renders error state for summary-backed metrics', () => {
    mockedUseAlertSummary.mockReturnValue({
      data: undefined,
      error: new Error('Summary failed'),
      isError: true,
      isLoading: false,
      refetch: jest.fn(),
    } as never)

    render(<MetricsGrid />)

    expect(screen.getByText('Unable to load summary')).toBeInTheDocument()
    expect(screen.getByText('Summary request failed')).toBeInTheDocument()
  })

  it('renders chart regions for alert and rate data', () => {
    render(<MetricsGrid />)

    expect(screen.getByLabelText('Total alert activity')).toBeInTheDocument()
    expect(screen.getByLabelText('Average alerts per minute by severity')).toBeInTheDocument()
  })
})

function rateSnapshot({ overallPerMinute, high = 0, medium = 0 }: { overallPerMinute: number; high?: number; medium?: number }): AlertRateSnapshot {
  return {
    timestamp: '2026-07-02T11:02:00Z',
    windowSeconds: 60,
    intervalSeconds: 5,
    overallPerMinute,
    bySeverityPerMinute: {
      LOW: 1,
      MEDIUM: medium,
      HIGH: high,
      CRITICAL: 0,
    },
    byRulePerMinute: {
      ACCESS_EVENT_RISK: overallPerMinute,
    },
  }
}

function alert({ id, severity, createdAt }: { id: number; severity: Alert['severity']; createdAt: string }): Alert {
  return {
    id,
    ruleName: 'ACCESS_EVENT_RISK',
    severity,
    message: 'Access event evaluated',
    createdAt,
    accessEvent: {
      id: id + 100,
      username: 'alice',
      tableName: 'customer_accounts',
      queryType: 'SELECT',
      occurredAt: createdAt,
      rowCount: 42,
      sourceIp: '10.0.0.12',
    },
  }
}