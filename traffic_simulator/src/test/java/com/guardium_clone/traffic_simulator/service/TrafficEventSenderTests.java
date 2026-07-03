package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import com.guardium_clone.traffic_simulator.messaging.RawIngestionEventPublisher;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

class TrafficEventSenderTests {

    @Test
    void sendEventPublishesGeneratedRequest() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        RawIngestionEventPublisher publisher = mock(RawIngestionEventPublisher.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(publisher.publish(request)).thenReturn(message(request));

        TrafficEventSender sender = new TrafficEventSender(factory, publisher, properties(1, Duration.ZERO));

        sender.sendEvent();

        verify(factory).nextEvent();
        verify(publisher).publish(request);
    }

    @Test
    void sendEventRetriesTransientPublishFailure() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        RawIngestionEventPublisher publisher = mock(RawIngestionEventPublisher.class);
        IngestEventRequest request = request();
        AtomicInteger sleeps = new AtomicInteger();
        when(factory.nextEvent()).thenReturn(request);
        when(publisher.publish(request))
                .thenThrow(new AmqpException("connection failed"))
                .thenReturn(message(request));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                publisher,
                properties(3, Duration.ofMillis(10)),
                duration -> sleeps.incrementAndGet()
        );

        sender.sendEvent();

        verify(publisher, times(2)).publish(request);
        assertThat(sleeps.get()).isEqualTo(1);
    }

    @Test
    void sendEventHandlesPublishFailureAfterConfiguredAttempts() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        RawIngestionEventPublisher publisher = mock(RawIngestionEventPublisher.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(publisher.publish(request)).thenThrow(new AmqpException("connection failed"));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                publisher,
                properties(3, Duration.ZERO),
                duration -> {
                }
        );

        sender.sendEvent();

        verify(publisher, times(3)).publish(request);
    }

    @Test
    void sendEventStopsRetryingWhenInterruptedDuringBackoff() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        RawIngestionEventPublisher publisher = mock(RawIngestionEventPublisher.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(publisher.publish(request)).thenThrow(new AmqpException("connection failed"));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                publisher,
                properties(3, Duration.ofMillis(10)),
                duration -> {
                    throw new InterruptedException("interrupted");
                }
        );

        sender.sendEvent();

        verify(publisher, times(1)).publish(request);
        verify(factory, times(1)).nextEvent();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private TrafficSimulatorProperties properties(int sendRetryAttempts, Duration sendRetryBackoff) {
        return new TrafficSimulatorProperties(
                false,
                1,
                false,
                sendRetryAttempts,
                sendRetryBackoff
        );
    }

    private RawIngestionEventMessage message(IngestEventRequest request) {
        return new RawIngestionEventMessage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                request.username(),
                request.tableName(),
                request.queryType().name(),
                request.occurredAt(),
                request.rowCount(),
                request.sourceIp(),
                request.queryText()
        );
    }

    private IngestEventRequest request() {
        return new IngestEventRequest(
                "alice",
                "customer_accounts",
                QueryType.SELECT,
                Instant.parse("2026-07-02T22:30:00Z"),
                42,
                "10.0.0.12",
                "select * from customer_accounts"
        );
    }
}