package com.guardium_clone.traffic_simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic-simulator.raw-events")
public record RawEventMessagingProperties(String exchange, String routingKey) {

    public RawEventMessagingProperties {
        if (exchange == null || exchange.isBlank()) {
            exchange = "guardium.ingestion-events";
        }
        if (routingKey == null || routingKey.isBlank()) {
            routingKey = "ingestion-event.created";
        }
    }
}