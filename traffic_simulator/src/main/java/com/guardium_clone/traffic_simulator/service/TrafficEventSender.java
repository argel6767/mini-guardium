package com.guardium_clone.traffic_simulator.service;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.config.TrafficSimulatorProperties;
import com.guardium_clone.traffic_simulator.messaging.RawIngestionEventPublisher;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrafficEventSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficEventSender.class);

    private final TrafficEventFactory trafficEventFactory;
    private final RawIngestionEventPublisher rawIngestionEventPublisher;
    private final TrafficSimulatorProperties properties;
    private final RetrySleeper retrySleeper;

    @Autowired
    public TrafficEventSender(
            TrafficEventFactory trafficEventFactory,
            RawIngestionEventPublisher rawIngestionEventPublisher,
            TrafficSimulatorProperties properties
    ) {
        this(trafficEventFactory, rawIngestionEventPublisher, properties, Thread::sleep);
    }

    TrafficEventSender(
            TrafficEventFactory trafficEventFactory,
            RawIngestionEventPublisher rawIngestionEventPublisher,
            TrafficSimulatorProperties properties,
            RetrySleeper retrySleeper
    ) {
        this.trafficEventFactory = trafficEventFactory;
        this.rawIngestionEventPublisher = rawIngestionEventPublisher;
        this.properties = properties;
        this.retrySleeper = retrySleeper;
    }

    public void sendEvent() {
        IngestEventRequest request = trafficEventFactory.nextEvent();
        for (int attempt = 1; attempt <= properties.sendRetryAttempts(); attempt++) {
            try {
                RawIngestionEventMessage message = rawIngestionEventPublisher.publish(request);
                LOGGER.info(
                        "Published simulated traffic event simulatedEventId={}, username={}, tableName={}, queryType={}",
                        message.simulatedEventId(),
                        request.username(),
                        request.tableName(),
                        request.queryType()
                );
                return;
            } catch (AmqpException exception) {
                if (attempt >= properties.sendRetryAttempts()) {
                    LOGGER.warn(
                            "Failed to publish simulated traffic event username={}, tableName={}, queryType={} after {} attempt(s)",
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

    private boolean waitBeforeRetry(IngestEventRequest request, int attempt, AmqpException exception) {
        Duration backoff = properties.sendRetryBackoff();
        LOGGER.debug(
                "Retrying simulated traffic event publish username={}, tableName={}, queryType={}, nextAttempt={}, backoff={}",
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
                    "Interrupted before retrying simulated traffic event publish username={}, tableName={}, queryType={}",
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