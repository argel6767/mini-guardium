package com.guardium_clone.evaluation_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessEventMessagingPropertiesTests {

    @Test
    void defaultsMatchIngestionAccessEventPublisher() {
        AccessEventMessagingProperties properties = new AccessEventMessagingProperties(null, null, null);

        assertThat(properties.exchange()).isEqualTo("guardium.access-events");
        assertThat(properties.queue()).isEqualTo("guardium.access-events.evaluation-service");
        assertThat(properties.routingKey()).isEqualTo("access-event.created");
    }

    @Test
    void keepsExplicitValues() {
        AccessEventMessagingProperties properties = new AccessEventMessagingProperties(
                "events.exchange",
                "events.queue",
                "events.created"
        );

        assertThat(properties.exchange()).isEqualTo("events.exchange");
        assertThat(properties.queue()).isEqualTo("events.queue");
        assertThat(properties.routingKey()).isEqualTo("events.created");
    }
}
