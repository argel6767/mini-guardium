package com.guardium_clone.ingestion_processor.messaging;

import com.guardium_clone.ingestion_processor.config.AccessEventMessagingProperties;
import com.guardium_clone.ingestion_processor.model.AccessEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessEventCreatedPublisher {

    private static final Logger LOGGER = LogManager.getLogger(AccessEventCreatedPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AccessEventMessagingProperties properties;

    public AccessEventCreatedPublisher(
            RabbitTemplate rabbitTemplate,
            AccessEventMessagingProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(AccessEvent accessEvent) {
        AccessEventCreatedMessage message = AccessEventCreatedMessage.from(accessEvent);
        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), message);
        LOGGER.debug(
                "Published access event created message accessEventId={}, exchange={}, routingKey={}",
                message.accessEventId(),
                properties.exchange(),
                properties.routingKey()
        );
    }
}