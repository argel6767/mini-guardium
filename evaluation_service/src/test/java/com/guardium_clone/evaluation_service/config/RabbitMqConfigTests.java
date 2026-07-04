package com.guardium_clone.evaluation_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class RabbitMqConfigTests {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void declaresDurableAccessEventExchangeQueueAndBinding() {
        AccessEventMessagingProperties properties = new AccessEventMessagingProperties(
                "events.exchange",
                "events.queue",
                "events.created"
        );

        DirectExchange exchange = config.accessEventsExchange(properties);
        Queue queue = config.accessEventsQueue(properties);
        Binding binding = config.accessEventsBinding(queue, exchange, properties);

        assertThat(exchange.getName()).isEqualTo("events.exchange");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo("events.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(binding.getDestination()).isEqualTo("events.queue");
        assertThat(binding.getExchange()).isEqualTo("events.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("events.created");
    }
}
