package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.api.IngestEventResponse;
import com.guardium_clone.traffic_simulator.client.IngestionClient;
import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class TrafficEventSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficEventSender.class);

    private final TrafficEventFactory trafficEventFactory;
    private final IngestionClient ingestionClient;
    private final TrafficSimulatorProperties properties;
    private final RetrySleeper retrySleeper;

    @Autowired
    public TrafficEventSender(
            TrafficEventFactory trafficEventFactory,
            IngestionClient ingestionClient,
            TrafficSimulatorProperties properties
    ) {
        this(trafficEventFactory, ingestionClient, properties, Thread::sleep);
    }

    TrafficEventSender(
            TrafficEventFactory trafficEventFactory,
            IngestionClient ingestionClient,
            TrafficSimulatorProperties properties,
            RetrySleeper retrySleeper
    ) {
        this.trafficEventFactory = trafficEventFactory;
        this.ingestionClient = ingestionClient;
        this.properties = properties;
        this.retrySleeper = retrySleeper;
    }

    public void sendEvent() {
        IngestEventRequest request = trafficEventFactory.nextEvent();
        for (int attempt = 1; attempt <= properties.sendRetryAttempts(); attempt++) {
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
                return;
            } catch (RestClientException exception) {
                if (attempt >= properties.sendRetryAttempts()) {
                    LOGGER.warn(
                            "Failed to send simulated traffic event username={}, tableName={}, queryType={} after {} attempt(s)",
                            request.username(),
                            request.tableName(),
                            request.queryType(),
                            attempt,
                            exception
                    );
                    return;
                }
                if (!waitBeforeRetry(request, attempt, exception)) {
                    return;
                }
            }
        }
    }

    private boolean waitBeforeRetry(IngestEventRequest request, int attempt, RestClientException exception) {
        Duration backoff = properties.sendRetryBackoff();
        LOGGER.debug(
                "Retrying simulated traffic event username={}, tableName={}, queryType={}, nextAttempt={}, backoff={}",
                request.username(),
                request.tableName(),
                request.queryType(),
                attempt + 1,
                backoff,
                exception
        );
        try {
            retrySleeper.sleep(backoff);
            return true;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            LOGGER.warn(
                    "Interrupted before retrying simulated traffic event username={}, tableName={}, queryType={}",
                    request.username(),
                    request.tableName(),
                    request.queryType(),
                    interruptedException
            );
            return false;
        }
    }

    @FunctionalInterface
    interface RetrySleeper {

        void sleep(Duration duration) throws InterruptedException;
    }
}

