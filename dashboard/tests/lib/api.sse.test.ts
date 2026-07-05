import {
  subscribeToAlertBatchStream,
  subscribeToAlertRateStream,
  subscribeToAlertSeverityStream,
  type EventSourceFactory,
} from '@/lib/api'

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

describe('evaluation SSE API client', () => {
  it('subscribes to severity events and heartbeat events', () => {
    const { eventSource, factory } = createEventSourceFactory()
    const onMessage = jest.fn()
    const onHeartbeat = jest.fn()

    const subscription = subscribeToAlertSeverityStream({
      onMessage,
      onHeartbeat,
      eventSourceFactory: factory,
    })

    expect(factory).toHaveBeenCalledWith('http://localhost:8081/alerts/stream/severity')

    eventSource.emit('alert.severity', {
      alertId: 10,
      severity: 'CRITICAL',
      ruleName: 'ACCESS_EVENT_RISK',
      createdAt: '2026-07-02T11:01:00Z',
    })
    eventSource.emit('heartbeat', '2026-07-02T11:02:00Z')

    expect(onMessage).toHaveBeenCalledWith({
      alertId: 10,
      severity: 'CRITICAL',
      ruleName: 'ACCESS_EVENT_RISK',
      createdAt: '2026-07-02T11:01:00Z',
    })
    expect(onHeartbeat).toHaveBeenCalledWith('2026-07-02T11:02:00Z')

    subscription.close()
    expect(eventSource.close).toHaveBeenCalledTimes(1)
  })

  it('subscribes to alert batches', () => {
    const { eventSource, factory } = createEventSourceFactory()
    const onMessage = jest.fn()

    subscribeToAlertBatchStream({ onMessage, eventSourceFactory: factory })

    expect(factory).toHaveBeenCalledWith('http://localhost:8081/alerts/stream/batches')

    eventSource.emit('alerts.batch', {
      alerts: [],
      batchSize: 0,
      sentAt: '2026-07-02T11:02:00Z',
    })

    expect(onMessage).toHaveBeenCalledWith({
      alerts: [],
      batchSize: 0,
      sentAt: '2026-07-02T11:02:00Z',
    })
  })

  it('subscribes to alert rate snapshots', () => {
    const { eventSource, factory } = createEventSourceFactory()
    const onMessage = jest.fn()

    subscribeToAlertRateStream({ onMessage, eventSourceFactory: factory })

    expect(factory).toHaveBeenCalledWith('http://localhost:8081/alerts/stream/rates')

    eventSource.emit('alerts.rate', {
      timestamp: '2026-07-02T11:02:00Z',
      windowSeconds: 60,
      intervalSeconds: 5,
      overallPerMinute: 2,
      bySeverityPerMinute: {
        LOW: 0,
        MEDIUM: 0,
        HIGH: 1,
        CRITICAL: 1,
      },
      byRulePerMinute: {
        ACCESS_EVENT_RISK: 2,
      },
    })

    expect(onMessage).toHaveBeenCalledWith({
      timestamp: '2026-07-02T11:02:00Z',
      windowSeconds: 60,
      intervalSeconds: 5,
      overallPerMinute: 2,
      bySeverityPerMinute: {
        LOW: 0,
        MEDIUM: 0,
        HIGH: 1,
        CRITICAL: 1,
      },
      byRulePerMinute: {
        ACCESS_EVENT_RISK: 2,
      },
    })
  })
})
