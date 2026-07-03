package com.guardium_clone.ingestion_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.raw-events")
public record RawIngestionMessagingProperties(String exchange, String queue, String routingKey) {

    public RawIngestionMessagingProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "guardium.ingestion-events";
        }
        if (queue == null || queue.isBlank()) {
            queue = "guardium.ingestion-events.ingestion-processor";
        }
        if (routingKey == null || routingKey.isBlank()) {
            routingKey = "ingestion-event.created";
        }
    }
}