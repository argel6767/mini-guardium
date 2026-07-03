package com.guardium_clone.traffic_simulator.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TrafficSimulationSchedulerTests {

    @Test
    void schedulerDoesNothingWhenSimulatorIsDisabled() {
        TrafficSimulationService service = mock(TrafficSimulationService.class);
        TrafficSimulationScheduler scheduler = new TrafficSimulationScheduler(
                new TrafficSimulatorProperties(
                        false,
                        5,
                        false,
                        1,
                        Duration.ZERO
                ),
                service
        );

        scheduler.sendScheduledTraffic();

        verify(service, never()).sendEvents(5);
    }

    @Test
    void schedulerSendsConfiguredBatchWhenEnabled() {
        TrafficSimulationService service = mock(TrafficSimulationService.class);
        TrafficSimulationScheduler scheduler = new TrafficSimulationScheduler(
                new TrafficSimulatorProperties(
                        true,
                        5,
                        false,
                        1,
                        Duration.ZERO
                ),
                service
        );

        scheduler.sendScheduledTraffic();

        verify(service).sendEvents(5);
    }
}
