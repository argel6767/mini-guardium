import { getAlertSummary, listAlerts } from '@/lib/api'

const createHttpMock = () => ({
  get: jest.fn(),
})

describe('evaluation REST API client', () => {
  it('requests paged alerts with supported filters and removes empty params', async () => {
    const http = createHttpMock()
    const response = {
      content: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
    }
    http.get.mockResolvedValue({ data: response })

    await expect(
      listAlerts(
        {
          severity: 'HIGH',
          username: 'alice',
          tableName: '',
          createdFrom: '2026-07-02T09:00:00Z',
          createdTo: undefined,
          page: 0,
          size: 25,
          sort: ['createdAt,desc', 'id,desc'],
        },
        http as never,
      ),
    ).resolves.toEqual(response)

    expect(http.get).toHaveBeenCalledWith('/alerts', {
      params: {
        severity: 'HIGH',
        username: 'alice',
        createdFrom: '2026-07-02T09:00:00Z',
        page: 0,
        size: 25,
        sort: ['createdAt,desc', 'id,desc'],
      },
    })
  })

  it('requests alert summary from evaluation service', async () => {
    const http = createHttpMock()
    const response = {
      totalAlerts: 2,
      latestAlertCreatedAt: '2026-07-02T11:01:00Z',
      countsBySeverity: {
        LOW: 1,
        MEDIUM: 0,
        HIGH: 0,
        CRITICAL: 1,
      },
    }
    http.get.mockResolvedValue({ data: response })

    await expect(getAlertSummary(http as never)).resolves.toEqual(response)

    expect(http.get).toHaveBeenCalledWith('/alerts/summary')
  })
})
