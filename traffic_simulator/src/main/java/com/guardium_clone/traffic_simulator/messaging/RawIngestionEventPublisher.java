package com.guardium_clone.traffic_simulator.messaging;

import com.guardium_clone.messaging.RawIngestionEventMessage;

import com.guardium_clone.traffic_simulator.api.IngestEventRequest;
import com.guardium_clone.traffic_simulator.config.RawEventMessagingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RawIngestionEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RawEventMessagingProperties properties;

    public RawIngestionEventPublisher(RabbitTemplate rabbitTemplate, RawEventMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public RawIngestionEventMessage publish(IngestEventRequest request) {
        RawIngestionEventMessage message = RawIngestionEventMessage.from(request);
        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), message);
        return message;
    }
}