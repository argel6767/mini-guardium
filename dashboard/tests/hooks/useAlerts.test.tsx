import { renderHook, waitFor } from '@testing-library/react'

import { useAlertSummary, useAlerts } from '@/hooks/useAlerts'
import { getAlertSummary, listAlerts } from '@/lib/api'
import { createQueryClientWrapper } from '../queryClientWrapper'

jest.mock('@/lib/api', () => ({
  getAlertSummary: jest.fn(),
  listAlerts: jest.fn(),
}))

const mockedGetAlertSummary = jest.mocked(getAlertSummary)
const mockedListAlerts = jest.mocked(listAlerts)

describe('alert query hooks', () => {
  beforeEach(() => {
    mockedGetAlertSummary.mockReset()
    mockedListAlerts.mockReset()
  })

  it('loads alerts with the provided filters', async () => {
    const response = {
      content: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
    }
    mockedListAlerts.mockResolvedValue(response)

    const { result } = renderHook(
      () =>
        useAlerts({
          severity: 'HIGH',
          username: 'alice',
          page: 0,
          size: 25,
        }),
      { wrapper: createQueryClientWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(mockedListAlerts).toHaveBeenCalledWith({
      severity: 'HIGH',
      username: 'alice',
      page: 0,
      size: 25,
    })
    expect(result.current.data).toEqual(response)
  })

  it('loads alert summary', async () => {
    const response = {
      totalAlerts: 3,
      latestAlertCreatedAt: '2026-07-02T11:01:00Z',
      countsBySeverity: {
        LOW: 1,
        MEDIUM: 0,
        HIGH: 1,
        CRITICAL: 1,
      },
    }
    mockedGetAlertSummary.mockResolvedValue(response)

    const { result } = renderHook(() => useAlertSummary(), {
      wrapper: createQueryClientWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(mockedGetAlertSummary).toHaveBeenCalledTimes(1)
    expect(result.current.data).toEqual(response)
  })
})
