package com.guardium_clone.evaluation_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "evaluation.access-events")
public record AccessEventMessagingProperties(String exchange, String queue, String routingKey) {

    public AccessEventMessagingProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "guardium.access-events";
        }
        if (queue == null || queue.isBlank()) {
            queue = "guardium.access-events.evaluation-service";
        }
        if (routingKey == null || routingKey.isBlank()) {
            routingKey = "access-event.created";
        }
    }
}
