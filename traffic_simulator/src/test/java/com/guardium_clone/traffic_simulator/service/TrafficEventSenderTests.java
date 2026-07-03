package com.guardium_clone.traffic_simulator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.api.IngestEventResponse;
import com.guardium_clone.traffic_simulator.client.IngestionClient;
import com.guardium_clone.traffic_simulator.model.QueryType;
import java.time.Instant;
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

        TrafficEventSender sender = new TrafficEventSender(factory, client);

        sender.sendEvent();

        verify(factory).nextEvent();
        verify(client).send(request);
    }

    @Test
    void sendEventHandlesClientFailure() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request)).thenThrow(new RestClientException("connection failed"));

        TrafficEventSender sender = new TrafficEventSender(factory, client);

        sender.sendEvent();

        verify(client, times(1)).send(request);
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
