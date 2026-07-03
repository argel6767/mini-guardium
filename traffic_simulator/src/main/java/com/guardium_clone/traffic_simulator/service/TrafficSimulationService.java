package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.api.IngestEventResponse;
import com.guardium_clone.traffic_simulator.client.IngestionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class TrafficSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficSimulationService.class);

    private final TrafficEventFactory trafficEventFactory;
    private final IngestionClient ingestionClient;

    public TrafficSimulationService(TrafficEventFactory trafficEventFactory, IngestionClient ingestionClient) {
        this.trafficEventFactory = trafficEventFactory;
        this.ingestionClient = ingestionClient;
    }

    public void sendEvents(int eventCount) {
        for (int index = 0; index < eventCount; index++) {
            sendEvent();
        }
    }

    private void sendEvent() {
        IngestEventRequest request = trafficEventFactory.nextEvent();
        try {
            IngestEventResponse response = ingestionClient.send(request);
            LOGGER.info(
                    "Sent simulated traffic event username={}, tableName={}, queryType={}, ingestionId={}, status={}",
                    request.username(),
                    request.tableName(),
                    request.queryType(),
                    response == null ? null : response.ingestionId(),
                    response == null ? null : response.status()
            );
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "Failed to send simulated traffic event username={}, tableName={}, queryType={}",
                    request.username(),
                    request.tableName(),
                    request.queryType(),
                    exception
            );
        }
    }
}
