package com.guardium_clone.traffic_simulator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "traffic-simulator.concurrent", havingValue = "true")
public class ConcurrentTrafficSimulationService implements TrafficSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentTrafficSimulationService.class);

    private final TrafficEventSender trafficEventSender;

    public ConcurrentTrafficSimulationService(TrafficEventSender trafficEventSender) {
        this.trafficEventSender = trafficEventSender;
    }

    @Override
    public void sendEvents(int eventCount) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int index = 0; index < eventCount; index++) {
                futures.add(executor.submit(trafficEventSender::sendEvent));
            }
            waitForSubmittedEvents(futures);
        }
    }

    private void waitForSubmittedEvents(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for simulated traffic events to finish", exception);
                return;
            } catch (ExecutionException exception) {
                LOGGER.warn("Unexpected simulated traffic event failure", exception.getCause());
            }
        }
    }
}
