package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrafficSimulationScheduler {

    private final TrafficSimulatorProperties properties;
    private final TrafficSimulationService trafficSimulationService;

    public TrafficSimulationScheduler(
            TrafficSimulatorProperties properties,
            TrafficSimulationService trafficSimulationService
    ) {
        this.properties = properties;
        this.trafficSimulationService = trafficSimulationService;
    }

    @Scheduled(fixedRateString = "${traffic-simulator.tick-rate:PT1S}")
    void sendScheduledTraffic() {
        if (!properties.enabled()) {
            return;
        }
        trafficSimulationService.sendEvents(properties.eventsPerTick());
    }
}
