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

class TrafficSimulationServiceTests {

    @Test
    void sendEventsSendsGeneratedRequests() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request)).thenReturn(new IngestEventResponse(1L, "PENDING", Instant.now()));

        TrafficSimulationService service = new TrafficSimulationService(factory, client);

        service.sendEvents(3);

        verify(factory, times(3)).nextEvent();
        verify(client, times(3)).send(request);
    }

    @Test
    void sendEventsContinuesAfterClientFailure() {
        TrafficEventFactory factory = mock(TrafficEventFactory.class);
        IngestionClient client = mock(IngestionClient.class);
        IngestEventRequest request = request();
        when(factory.nextEvent()).thenReturn(request);
        when(client.send(request))
                .thenThrow(new RestClientException("connection failed"))
                .thenReturn(new IngestEventResponse(2L, "PENDING", Instant.now()));

        TrafficSimulationService service = new TrafficSimulationService(factory, client);

        service.sendEvents(2);

        verify(client, times(2)).send(request);
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
