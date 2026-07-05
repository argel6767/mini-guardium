import axios, { type AxiosInstance } from 'axios'

import { evaluationApiBaseUrl } from '@/lib/config'

export const evaluationApi = axios.create({
  baseURL: evaluationApiBaseUrl,
  headers: {
    Accept: 'application/json',
  },
})

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type QueryType = 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'CREATE' | 'ALTER' | 'DROP' | 'OTHER'

export type AlertAccessEvent = {
  id: number
  username: string
  tableName: string
  queryType: QueryType
  occurredAt: string
  rowCount: number
  sourceIp: string
}

export type Alert = {
  id: number
  ruleName: string
  severity: AlertSeverity
  message: string
  createdAt: string
  accessEvent: AlertAccessEvent
}

export type PagedResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AlertListParams = {
  severity?: AlertSeverity
  ruleName?: string
  tableName?: string
  username?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
  sort?: string | string[]
}

export type AlertSummary = {
  totalAlerts: number
  latestAlertCreatedAt: string | null
  countsBySeverity: Record<AlertSeverity, number>
}

export type AlertSeverityEvent = {
  alertId: number
  severity: AlertSeverity
  ruleName: string
  createdAt: string
}

export type AlertBatch = {
  alerts: Alert[]
  batchSize: number
  sentAt: string
}

export type AlertRateSnapshot = {
  timestamp: string
  windowSeconds: number
  intervalSeconds: number
  overallPerMinute: number
  bySeverityPerMinute: Record<AlertSeverity, number>
  byRulePerMinute: Record<string, number>
}

export type EventSourceFactory = (url: string) => EventSource

export type StreamSubscription = {
  close: () => void
}

export type StreamOptions<T> = {
  onMessage: (payload: T) => void
  onHeartbeat?: (timestamp: string) => void
  onError?: (event: Event) => void
  eventSourceFactory?: EventSourceFactory
}

export async function listAlerts(params: AlertListParams = {}, http: AxiosInstance = evaluationApi) {
  const response = await http.get<PagedResponse<Alert>>('/alerts', {
    params: removeEmptyParams(params),
  })

  return response.data
}

export async function getAlertSummary(http: AxiosInstance = evaluationApi) {
  const response = await http.get<AlertSummary>('/alerts/summary')

  return response.data
}

export function subscribeToAlertSeverityStream(options: StreamOptions<AlertSeverityEvent>) {
  return subscribeToStream('/alerts/stream/severity', 'alert.severity', options)
}

export function subscribeToAlertBatchStream(options: StreamOptions<AlertBatch>) {
  return subscribeToStream('/alerts/stream/batches', 'alerts.batch', options)
}

export function subscribeToAlertRateStream(options: StreamOptions<AlertRateSnapshot>) {
  return subscribeToStream('/alerts/stream/rates', 'alerts.rate', options)
}

function subscribeToStream<T>(path: string, eventName: string, options: StreamOptions<T>): StreamSubscription {
  const eventSource = (options.eventSourceFactory ?? createEventSource)(toEvaluationApiUrl(path))

  eventSource.addEventListener(eventName, (event) => {
    options.onMessage(parseMessageEvent<T>(event))
  })

  if (options.onHeartbeat) {
    eventSource.addEventListener('heartbeat', (event) => {
      options.onHeartbeat?.(parseMessageEvent<string>(event))
    })
  }

  if (options.onError) {
    eventSource.addEventListener('error', options.onError)
  }

  return {
    close: () => eventSource.close(),
  }
}

function createEventSource(url: string) {
  return new EventSource(url)
}

function parseMessageEvent<T>(event: Event) {
  return JSON.parse((event as MessageEvent<string>).data) as T
}

function toEvaluationApiUrl(path: string) {
  return `${evaluationApiBaseUrl.replace(/\/$/, '')}${path}`
}

function removeEmptyParams(params: AlertListParams) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

