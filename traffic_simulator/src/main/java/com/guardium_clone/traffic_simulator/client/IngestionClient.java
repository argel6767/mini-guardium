package com.guardium_clone.traffic_simulator.client;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.api.IngestEventResponse;
import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IngestionClient {

    private final RestClient restClient;
    private final TrafficSimulatorProperties properties;

    public IngestionClient(RestClient.Builder restClientBuilder, TrafficSimulatorProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public IngestEventResponse send(IngestEventRequest request) {
        return restClient.post()
                .uri(properties.ingestionApiUrl())
                .body(request)
                .retrieve()
                .body(IngestEventResponse.class);
    }
}
