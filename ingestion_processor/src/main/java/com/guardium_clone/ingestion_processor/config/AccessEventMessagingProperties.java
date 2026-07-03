package com.guardium_clone.ingestion_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.events")
public record AccessEventMessagingProperties(String exchange, String routingKey) {

    public AccessEventMessagingProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "guardium.access-events";
        }
        if (routingKey == null || routingKey.isBlank()) {
            routingKey = "access-event.created";
        }
    }
}