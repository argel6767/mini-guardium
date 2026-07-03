package com.guardium_clone.traffic_simulator.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic-simulator")
public record TrafficSimulatorProperties(
        boolean enabled,
        URI ingestionApiUrl,
        int eventsPerTick,
        boolean concurrent
) {

    public TrafficSimulatorProperties {
        if (ingestionApiUrl == null) {
            ingestionApiUrl = URI.create("http://localhost:8080/events");
        }
        if (eventsPerTick < 1) {
            eventsPerTick = 1;
        }
    }
}
