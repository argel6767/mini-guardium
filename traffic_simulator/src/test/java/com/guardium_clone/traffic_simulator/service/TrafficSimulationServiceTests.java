package com.guardium_clone.traffic_simulator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SequentialTrafficSimulationServiceTests {

    @Test
    void sendEventsSendsRequestedNumberOfEventsSequentially() {
        TrafficEventSender sender = mock(TrafficEventSender.class);
        SequentialTrafficSimulationService service = new SequentialTrafficSimulationService(sender);

        service.sendEvents(3);

        verify(sender, times(3)).sendEvent();
    }
}
