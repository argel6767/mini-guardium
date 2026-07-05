import { useEffect } from 'react'

import {
  subscribeToAlertBatchStream,
  subscribeToAlertRateStream,
  subscribeToAlertSeverityStream,
  type AlertBatch,
  type AlertRateSnapshot,
  type AlertSeverityEvent,
  type EventSourceFactory,
} from '@/lib/api'

type AlertStreamOptions<T> = {
  enabled?: boolean
  onMessage: (payload: T) => void
  onHeartbeat?: (timestamp: string) => void
  onError?: (event: Event) => void
  eventSourceFactory?: EventSourceFactory
}

export function useAlertSeverityStream({
  enabled = true,
  onMessage,
  onHeartbeat,
  onError,
  eventSourceFactory,
}: AlertStreamOptions<AlertSeverityEvent>) {
  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    const subscription = subscribeToAlertSeverityStream({
      onMessage,
      onHeartbeat,
      onError,
      eventSourceFactory,
    })

    return () => subscription.close()
  }, [enabled, onMessage, onHeartbeat, onError, eventSourceFactory])
}

export function useAlertBatchStream({
  enabled = true,
  onMessage,
  onHeartbeat,
  onError,
  eventSourceFactory,
}: AlertStreamOptions<AlertBatch>) {
  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    const subscription = subscribeToAlertBatchStream({
      onMessage,
      onHeartbeat,
      onError,
      eventSourceFactory,
    })

    return () => subscription.close()
  }, [enabled, onMessage, onHeartbeat, onError, eventSourceFactory])
}

export function useAlertRateStream({
  enabled = true,
  onMessage,
  onHeartbeat,
  onError,
  eventSourceFactory,
}: AlertStreamOptions<AlertRateSnapshot>) {
  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    const subscription = subscribeToAlertRateStream({
      onMessage,
      onHeartbeat,
      onError,
      eventSourceFactory,
    })

    return () => subscription.close()
  }, [enabled, onMessage, onHeartbeat, onError, eventSourceFactory])
}
