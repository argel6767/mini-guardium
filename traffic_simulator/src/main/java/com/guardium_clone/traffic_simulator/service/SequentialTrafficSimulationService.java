package com.guardium_clone.traffic_simulator.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "traffic-simulator.concurrent", havingValue = "false", matchIfMissing = true)
public class SequentialTrafficSimulationService implements TrafficSimulationService {

    private final TrafficEventSender trafficEventSender;

    public SequentialTrafficSimulationService(TrafficEventSender trafficEventSender) {
        this.trafficEventSender = trafficEventSender;
    }

    @Override
    public void sendEvents(int eventCount) {
        for (int index = 0; index < eventCount; index++) {
            trafficEventSender.sendEvent();
        }
    }
}
