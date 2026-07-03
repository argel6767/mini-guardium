package com.guardium_clone.traffic_simulator.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic-simulator")
public record TrafficSimulatorProperties(
        boolean enabled,
        URI ingestionApiUrl,
        int eventsPerTick,
        boolean concurrent,
        int sendRetryAttempts,
        Duration sendRetryBackoff
) {

    public TrafficSimulatorProperties {
        if (ingestionApiUrl == null) {
            ingestionApiUrl = URI.create("http://localhost:8080/events");
        }
        if (eventsPerTick < 1) {
            eventsPerTick = 1;
        }
        if (sendRetryAttempts < 1) {
            sendRetryAttempts = 1;
        }
        if (sendRetryBackoff == null || sendRetryBackoff.isNegative()) {
            sendRetryBackoff = Duration.ofMillis(250);
        }
    }
}
