package com.guardium_clone.traffic_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.api.IngestEventResponse;
import com.guardium_clone.traffic_simulator.client.IngestionClient;
import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class TrafficEventSenderTests {

    @Test
    void sendEventSendsGeneratedRequest() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request)).thenReturn(new IngestEventResponse(1L, "PENDING", Instant.now()));

        TrafficEventSender sender = new TrafficEventSender(factory, client, properties(1, Duration.ZERO));

        sender.sendEvent();

        verify(factory).nextEvent();
        verify(client).send(request);
    }

    @Test
    void sendEventRetriesTransientClientFailure() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        AtomicInteger sleeps = new AtomicInteger();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request))
                .thenThrow(new RestClientException("connection failed"))
                .thenReturn(new IngestEventResponse(1L, "PENDING", Instant.now()));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                client,
                properties(3, Duration.ofMillis(10)),
                duration -> sleeps.incrementAndGet()
        );

        sender.sendEvent();

        verify(client, times(2)).send(request);
        assertThat(sleeps.get()).isEqualTo(1);
    }

    @Test
    void sendEventHandlesClientFailureAfterConfiguredAttempts() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request)).thenThrow(new RestClientException("connection failed"));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                client,
                properties(3, Duration.ZERO),
                duration -> {
                }
        );

        sender.sendEvent();

        verify(client, times(3)).send(request);
    }

    @Test
    void sendEventStopsRetryingWhenInterruptedDuringBackoff() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request)).thenThrow(new RestClientException("connection failed"));

        TrafficEventSender sender = new TrafficEventSender(
                factory,
                client,
                properties(3, Duration.ofMillis(10)),
                duration -> {
                    throw new InterruptedException("interrupted");
                }
        );

        sender.sendEvent();

        verify(client, times(1)).send(request);
        verify(factory, times(1)).nextEvent();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private TrafficSimulatorProperties properties(int sendRetryAttempts, Duration sendRetryBackoff) {
        return new TrafficSimulatorProperties(
                false,
                URI.create("http://localhost:8080/events"),
                1,
                false,
                sendRetryAttempts,
                sendRetryBackoff
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

