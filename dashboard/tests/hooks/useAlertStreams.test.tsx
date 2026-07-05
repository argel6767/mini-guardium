import { renderHook } from '@testing-library/react'

import { useAlertBatchStream, useAlertRateStream, useAlertSeverityStream } from '@/hooks/useAlertStreams'
import type { EventSourceFactory } from '@/lib/api'

type Listener = (event: MessageEvent<string>) => void

class FakeEventSource {
  readonly listeners = new Map<string, Listener>()
  close = jest.fn()

  addEventListener(eventName: string, listener: EventListenerOrEventListenerObject) {
    this.listeners.set(eventName, listener as Listener)
  }

  emit(eventName: string, payload: unknown) {
    this.listeners.get(eventName)?.({ data: JSON.stringify(payload) } as MessageEvent<string>)
  }
}

function createEventSourceFactory() {
  const eventSource = new FakeEventSource()
  const factory: EventSourceFactory = jest.fn(() => eventSource as unknown as EventSource)

  return { eventSource, factory }
}

describe('alert stream hooks', () => {
  it('subscribes to severity stream and closes on unmount', () => {
    const { eventSource, factory } = createEventSourceFactory()
    const onMessage = jest.fn()

    const { unmount } = renderHook(() =>
      useAlertSeverityStream({
        onMessage,
        eventSourceFactory: factory,
      }),
    )

    eventSource.emit('alert.severity', {
      alertId: 7,
      severity: 'HIGH',
      ruleName: 'ACCESS_EVENT_RISK',
      createdAt: '2026-07-02T11:01:00Z',
    })

    expect(onMessage).toHaveBeenCalledWith({
      alertId: 7,
      severity: 'HIGH',
      ruleName: 'ACCESS_EVENT_RISK',
      createdAt: '2026-07-02T11:01:00Z',
    })

    unmount()
    expect(eventSource.close).toHaveBeenCalledTimes(1)
  })

  it('does not subscribe when disabled', () => {
    const { factory } = createEventSourceFactory()

    renderHook(() =>
      useAlertBatchStream({
        enabled: false,
        onMessage: jest.fn(),
        eventSourceFactory: factory,
      }),
    )

    expect(factory).not.toHaveBeenCalled()
  })

  it('subscribes to batch and rate streams', () => {
    const batch = createEventSourceFactory()
    const rate = createEventSourceFactory()
    const onBatch = jest.fn()
    const onRate = jest.fn()

    renderHook(() =>
      useAlertBatchStream({
        onMessage: onBatch,
        eventSourceFactory: batch.factory,
      }),
    )
    renderHook(() =>
      useAlertRateStream({
        onMessage: onRate,
        eventSourceFactory: rate.factory,
      }),
    )

    batch.eventSource.emit('alerts.batch', {
      alerts: [],
      batchSize: 0,
      sentAt: '2026-07-02T11:02:00Z',
    })
    rate.eventSource.emit('alerts.rate', {
      timestamp: '2026-07-02T11:02:00Z',
      windowSeconds: 60,
      intervalSeconds: 5,
      overallPerMinute: 1,
      bySeverityPerMinute: {
        LOW: 0,
        MEDIUM: 0,
        HIGH: 1,
        CRITICAL: 0,
      },
      byRulePerMinute: {
        ACCESS_EVENT_RISK: 1,
      },
    })

    expect(onBatch).toHaveBeenCalledWith({
      alerts: [],
      batchSize: 0,
      sentAt: '2026-07-02T11:02:00Z',
    })
    expect(onRate).toHaveBeenCalledWith({
      timestamp: '2026-07-02T11:02:00Z',
      windowSeconds: 60,
      intervalSeconds: 5,
      overallPerMinute: 1,
      bySeverityPerMinute: {
        LOW: 0,
        MEDIUM: 0,
        HIGH: 1,
        CRITICAL: 0,
      },
      byRulePerMinute: {
        ACCESS_EVENT_RISK: 1,
      },
    })
  })
})
