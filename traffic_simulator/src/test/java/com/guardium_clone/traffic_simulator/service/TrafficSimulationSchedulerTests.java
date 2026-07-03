package com.guardium_clone.traffic_simulator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TrafficSimulationSchedulerTests {

    @Test
    void schedulerDoesNothingWhenSimulatorIsDisabled() {
        TrafficSimulationService service = mock(TrafficSimulationService.class);
        TrafficSimulationScheduler scheduler = new TrafficSimulationScheduler(
                new TrafficSimulatorProperties(false, URI.create("http://localhost:8080/events"), 5, false),
                service
        );

        scheduler.sendScheduledTraffic();

        verify(service, never()).sendEvents(5);
    }

    @Test
    void schedulerSendsConfiguredBatchWhenEnabled() {
        TrafficSimulationService service = mock(TrafficSimulationService.class);
        TrafficSimulationScheduler scheduler = new TrafficSimulationScheduler(
                new TrafficSimulatorProperties(true, URI.create("http://localhost:8080/events"), 5, false),
                service
        );

        scheduler.sendScheduledTraffic();

        verify(service).sendEvents(5);
    }
}
