import { useQuery, type UseQueryOptions } from '@tanstack/react-query'

import {
  getAlertSummary,
  listAlerts,
  type Alert,
  type AlertListParams,
  type AlertSummary,
  type PagedResponse,
} from '@/lib/api'

export const alertQueryKeys = {
  all: ['alerts'] as const,
  lists: () => [...alertQueryKeys.all, 'list'] as const,
  list: (params: AlertListParams = {}) => [...alertQueryKeys.lists(), params] as const,
  summary: () => [...alertQueryKeys.all, 'summary'] as const,
}

type AlertsQueryOptions = Omit<
  UseQueryOptions<PagedResponse<Alert>, Error>,
  'queryKey' | 'queryFn'
>

type AlertSummaryQueryOptions = Omit<UseQueryOptions<AlertSummary, Error>, 'queryKey' | 'queryFn'>

export function useAlerts(params: AlertListParams = {}, options?: AlertsQueryOptions) {
  return useQuery({
    queryKey: alertQueryKeys.list(params),
    queryFn: () => listAlerts(params),
    ...options,
  })
}

export function useAlertSummary(options?: AlertSummaryQueryOptions) {
  return useQuery({
    queryKey: alertQueryKeys.summary(),
    queryFn: () => getAlertSummary(),
    ...options,
  })
}

